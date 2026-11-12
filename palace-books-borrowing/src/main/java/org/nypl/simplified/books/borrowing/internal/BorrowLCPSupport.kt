package org.nypl.simplified.books.borrowing.internal

import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.lcpNotSupported
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskCancelled
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedAcquisitionPaths
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpError
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Support functionality for LCP books.
 */

object BorrowLCPSupport {
  /**
   * Check that we actually have the required DRM support.
   */

  fun checkDRMSupport(context: BorrowContextType) {
    context.taskRecorder.beginNewStep("Checking for LCP support...")
    if (context.lcpService == null) {
      context.taskRecorder.currentStepFailed(
        message = "This build of the application does not support LCP DRM.",
        errorCode = lcpNotSupported,
        extraMessages = listOf(),
      )
      throw BorrowSubtaskFailed()
    }
  }

  fun downloadLicense(
    context: BorrowContextType,
    target: URI,
  ): ByteArray {
    context.logDebug("Downloading license from {}", target)
    context.taskRecorder.beginNewStep("Downloading license from $target...")
    context.taskRecorder.addAttribute("URI", target.toString())
    context.checkCancelled()

    val temporaryFile = context.temporaryFile()

    return try {
      val downloadRequest =
        BorrowHTTP.createDownloadRequest(
          context = context,
          target = target,
          outputFile = temporaryFile,
        )

      when (val result = LSHTTPDownloads.download(downloadRequest)) {
        DownloadCancelled -> {
          throw BorrowSubtaskCancelled()
        }

        is DownloadFailedServer -> {
          throw BorrowHTTP.onDownloadFailedServer(
            context = context,
            result = result,
          )
        }

        is DownloadFailedUnacceptableMIME -> {
          throw BorrowSubtaskFailed()
        }

        is DownloadFailedExceptionally -> {
          throw BorrowHTTP.onDownloadFailedExceptionally(context, result)
        }

        is DownloadCompletedSuccessfully -> {
          temporaryFile.readBytes()
        }
      }
    } finally {
      temporaryFile.delete()
    }
  }

  /**
   * Find a passphrase for the given LCP book from an associated loans feed.
   */

  fun findPassphraseOrManual(context: BorrowContextType): String {
    context.bookDownloadIsRunning("Locating passphrase…")
    return try {
      this.findPassphrase(context)
    } catch (e: Exception) {
      ""
    }
  }

  /**
   * Find a passphrase for the given LCP book from an associated loans feed.
   */

  fun findPassphrase(context: BorrowContextType): String {
    context.taskRecorder.beginNewStep("Retrieving LCP hashed passphrase…")
    context.bookDownloadIsRunning("Locating passphrase…")

    val loansURI = context.account.provider.loansURI
    if (loansURI == null) {
      context.taskRecorder.currentStepFailed(
        message = "No loans URI provided; unable to retrieve a passphrase.",
        errorCode = "lcpMissingLoans",
        extraMessages = listOf(),
      )
      throw BorrowSubtaskFailed()
    }

    val feedLoader = context.services.requireService(FeedLoaderType::class.java)
    val credentials = context.takeSubtaskCredentialsRequiringAccount()

    val result =
      feedLoader
        .fetchURI(
          accountID = context.account.id,
          uri = loansURI,
          credentials = credentials,
          method = "GET",
        ).get(30L, TimeUnit.SECONDS)

    context.bookDownloadIsRunning("Locating passphrase…")

    when (result) {
      is FeedLoaderResult.FeedLoaderSuccess -> {
        val accessToken = result.accessToken
        if (accessToken != null) {
          context.account.updateBasicTokenCredentials(accessToken)
        }
        return this.findPassphraseInFeed(context, result.feed)
      }

      is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication -> {
        context.taskRecorder.currentStepFailed(
          message = "Authentication failed when fetching loans feed.",
          errorCode = BorrowErrorCodes.httpRequestFailed,
          exception = result.exception,
          extraMessages = listOf(),
        )
        throw BorrowSubtaskFailed()
      }

      is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral -> {
        context.taskRecorder.currentStepFailed(
          message = result.message,
          errorCode = BorrowErrorCodes.httpRequestFailed,
          exception = result.exception,
          extraMessages = listOf(),
        )
        throw BorrowSubtaskFailed()
      }
    }
  }

  private fun findPassphraseInFeed(
    context: BorrowContextType,
    feed: Feed,
  ): String {
    val entryFound =
      when (feed) {
        is FeedData -> {
          findEntryByID(feed.entries, context.bookCurrent.entry.id)
        }

        is FeedDataWithGroups -> {
          findEntryByIDInGroups(feed.groups, context.bookCurrent.entry.id)
        }
      }

    if (entryFound == null) {
      context.taskRecorder.currentStepFailed(
        message = "Unable to locate the current book in the user's loans feed.",
        errorCode = "lcpMissingEntryInLoansFeed",
        exception = null,
        extraMessages = listOf(),
      )
      throw BorrowSubtaskFailed()
    }

    val linearized = FeedAcquisitionPaths.linearize(entryFound.acquisitions)
    for (path in linearized) {
      for (element in path.elements) {
        val passphrase = element.properties["lcp:hashed_passphrase"]
        if (passphrase != null) {
          context.taskRecorder.currentStepSucceeded("Found LCP passphrase")
          return passphrase
        }
      }
    }

    context.taskRecorder.currentStepFailed(
      message = "No LCP hashed passphrase was provided.",
      errorCode = "lcpMissingPassphrase",
      extraMessages = listOf(),
    )
    throw BorrowSubtaskFailed()
  }

  private fun findEntryByID(
    entries: List<FeedEntry>,
    bookID: String,
  ): FeedEntryBook? {
    for (entry in entries) {
      when (entry) {
        is FeedEntryBook -> {
          if (entry.id == bookID) return entry
        }

        is org.nypl.simplified.feeds.api.FeedEntryCorrupt -> {
          Unit
        }
      }
    }
    return null
  }

  private fun findEntryByIDInGroups(
    groups: List<org.nypl.simplified.feeds.api.FeedGroup>,
    bookID: String,
  ): FeedEntryBook? {
    for (group in groups) {
      val found = findEntryByID(group.groupEntries, bookID)
      if (found != null) return found
    }
    return null
  }

  fun fetchAllR2ErrorMessages(failure: Try.Failure<*, Error>): List<String> {
    val messages = mutableListOf<String>()
    var errorNow: Error? = failure.value
    while (true) {
      if (errorNow == null) {
        break
      }
      messages.add(errorNow.message)
      when (val e = errorNow) {
        is HttpError.ErrorResponse -> {
          messages.add("URL returned HTTP status ${e.status}.")
          val problemDetails = e.problemDetails
          if (problemDetails != null) {
            messages.add("Problem details [Title]:    ${problemDetails.title}")
            messages.add("Problem details [Detail]:   ${problemDetails.detail}")
            messages.add("Problem details [Type]:     ${problemDetails.type}")
            messages.add("Problem details [Instance]: ${problemDetails.instance}")
            messages.add("Problem details [Status]:   ${problemDetails.status}")
          }
        }
      }
      errorNow = errorNow.cause
    }
    return messages.toList()
  }
}
