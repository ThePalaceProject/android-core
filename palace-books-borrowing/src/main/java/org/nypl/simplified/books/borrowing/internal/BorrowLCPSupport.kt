package org.nypl.simplified.books.borrowing.internal

import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addBasicTokenPropertiesIfApplicable
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.lcpNotSupported
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskCancelled
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.opds.core.OPDSAcquisitionPaths
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpError
import java.net.URI

/**
 * Support functionality for LCP books.
 */

object BorrowLCPSupport {

  /**
   * Check that we actually have the required DRM support.
   */

  fun checkDRMSupport(
    context: BorrowContextType
  ) {
    context.taskRecorder.beginNewStep("Checking for LCP support...")
    if (context.lcpService == null) {
      context.taskRecorder.currentStepFailed(
        message = "This build of the application does not support LCP DRM.",
        errorCode = lcpNotSupported,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }
  }

  fun downloadLicense(
    context: BorrowContextType,
    target: URI
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
          outputFile = temporaryFile
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

  fun findPassphraseOrManual(
    context: BorrowContextType
  ): String {
    context.bookDownloadIsRunning("Locating passphrase…")
    return if (context.isManualLCPPassphraseEnabled) {
      // if the manual input for the LCP passphrase is enabled, we need to catch a possible
      // exception while fetching the current passphrase as it may be possible for the user to
      // manually input it and if the exception isn't caught, the download will immediately fail.
      try {
        this.findPassphrase(context)
      } catch (e: Exception) {
        ""
      }
    } else {
      this.findPassphrase(context)
    }
  }

  /**
   * Find a passphrase for the given LCP book from an associated loans feed.
   */

  fun findPassphrase(
    context: BorrowContextType
  ): String {
    context.taskRecorder.beginNewStep("Retrieving LCP hashed passphrase…")
    context.bookDownloadIsRunning("Locating passphrase…")

    val loansURI = context.account.provider.loansURI
    if (loansURI == null) {
      context.taskRecorder.currentStepFailed(
        message = "No loans URI provided; unable to retrieve a passphrase.",
        errorCode = "lcpMissingLoans",
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }

    val credentials =
      context.takeSubtaskCredentialsRequiringAccount()
    val auth =
      AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

    val request =
      context.httpClient.newRequest(loansURI)
        .setAuthorization(auth)
        .addBasicTokenPropertiesIfApplicable(credentials)
        .build()

    return request.execute().use { response ->
      context.bookDownloadIsRunning("Locating passphrase…")

      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          context.account.updateBasicTokenCredentials(status.getAccessToken())
          this.findPassphraseHandleOK(context, loansURI, status)
        }

        is LSHTTPResponseStatus.Responded.Error -> {
          this.findPassphraseHandleError(context, status)
        }

        is LSHTTPResponseStatus.Failed -> {
          this.findPassphraseHandleFailure(context, status)
        }
      }
    }
  }

  private fun findPassphraseHandleFailure(
    context: BorrowContextType,
    status: LSHTTPResponseStatus.Failed
  ): String {
    context.taskRecorder.currentStepFailed(
      message = status.exception.message ?: "Exception raised during connection attempt.",
      errorCode = BorrowErrorCodes.httpConnectionFailed,
      exception = status.exception,
      extraMessages = listOf()
    )
    throw BorrowSubtaskFailed()
  }

  private fun findPassphraseHandleError(
    context: BorrowContextType,
    status: LSHTTPResponseStatus.Responded.Error
  ): String {
    val report = status.properties.problemReport
    if (report != null) {
      context.taskRecorder.addAttributes(report.toMap())
    }
    context.taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
      errorCode = BorrowErrorCodes.httpRequestFailed,
      exception = null,
      extraMessages = listOf()
    )
    throw BorrowSubtaskFailed()
  }

  private fun findPassphraseHandleOK(
    context: BorrowContextType,
    loansURI: URI,
    status: LSHTTPResponseStatus.Responded.OK
  ): String {
    val feedParser =
      context.services.requireService(OPDSFeedParserType::class.java)

    val entryFound = try {
      val result = feedParser.parse(loansURI, status.bodyStream)
      result.feedEntries.find { entry -> entry.id == context.bookCurrent.entry.id }
    } catch (e: Exception) {
      context.taskRecorder.currentStepFailed(
        message = "Unable to parse loans feed (${e.message})",
        errorCode = "lcpUnparseableLoansFeed",
        exception = e,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }

    if (entryFound == null) {
      context.taskRecorder.currentStepFailed(
        message = "Unable to locate the current book in the user's loans feed.",
        errorCode = "lcpMissingEntryInLoansFeed",
        exception = null,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }

    val linearized = OPDSAcquisitionPaths.linearize(entryFound)
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
      extraMessages = listOf()
    )
    throw BorrowSubtaskFailed()
  }

  fun fetchAllR2ErrorMessages(
    failure: Try.Failure<*, Error>
  ): List<String> {
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
