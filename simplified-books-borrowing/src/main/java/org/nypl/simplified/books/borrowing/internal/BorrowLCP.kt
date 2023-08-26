package org.nypl.simplified.books.borrowing.internal

import net.java.truevfs.access.TConfig
import net.java.truevfs.access.TFile
import net.java.truevfs.access.TVFS
import net.java.truevfs.kernel.spec.FsAccessOption
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.lcpNotSupported
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskCancelled
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskHaltedEarly
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.opds.core.OPDSAcquisitionPaths
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.readium.r2.lcp.LcpException
import org.readium.r2.lcp.license.model.LicenseDocument
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI

/**
 * A task that downloads an LCP license and then fulfills a book.
 */

class BorrowLCP private constructor() : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "LCP Download"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowLCP()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: URI?,
      account: AccountReadableType?
    ): Boolean {
      return MIMECompatibility.isCompatibleStrictWithoutAttributes(
        type,
        StandardFormatNames.lcpLicenseFiles
      )
    }
  }

  override fun execute(context: BorrowContextType) {
    try {
      this.checkDRMSupport(context)

      context.taskRecorder.beginNewStep("Downloading LCP license…")
      context.bookDownloadIsRunning(
        "Downloading...",
        receivedSize = 0L,
        expectedSize = 100L,
        bytesPerSecond = 1L
      )

      val passphrase = if (context.isManualLCPPassphraseEnabled) {
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

      val currentURI = context.currentURICheck()
      context.logDebug("downloading {}", currentURI)
      context.taskRecorder.beginNewStep("Downloading $currentURI...")
      context.taskRecorder.addAttribute("URI", currentURI.toString())
      context.checkCancelled()

      val temporaryFile = context.temporaryFile()

      try {
        val downloadRequest =
          BorrowHTTP.createDownloadRequest(
            context = context,
            target = currentURI,
            outputFile = temporaryFile
          )

        when (val result = LSHTTPDownloads.download(downloadRequest)) {
          DownloadCancelled -> {
            throw BorrowSubtaskCancelled()
          }
          is DownloadFailedServer -> {
            throw BorrowHTTP.onDownloadFailedServer(context, result)
          }
          is DownloadFailedUnacceptableMIME -> {
            throw BorrowSubtaskFailed()
          }
          is DownloadFailedExceptionally -> {
            throw BorrowHTTP.onDownloadFailedExceptionally(context, result)
          }
          is DownloadCompletedSuccessfully -> {
            this.fulfill(context, temporaryFile.readBytes(), passphrase)
          }
        }
      } finally {
        temporaryFile.delete()
      }
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private fun findPassphrase(context: BorrowContextType): String {
    context.taskRecorder.beginNewStep("Retrieving LCP hashed passphrase…")

    val loansURI = context.account.provider.loansURI
    if (loansURI == null) {
      context.taskRecorder.currentStepFailed(
        message = "No loans URI provided; unable to retrieve a passphrase.",
        errorCode = "lcpMissingLoans"
      )
      throw BorrowSubtaskFailed()
    }

    val credentials = context.account.loginState.credentials

    val auth =
      AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

    val request =
      context.httpClient.newRequest(loansURI)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .build()

    return request.execute().use { response ->
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
      exception = status.exception
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
      exception = null
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
        exception = e
      )
      throw BorrowSubtaskFailed()
    }

    if (entryFound == null) {
      context.taskRecorder.currentStepFailed(
        message = "Unable to locate the current book in the user's loans feed.",
        errorCode = "lcpMissingEntryInLoansFeed",
        exception = null
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
      errorCode = "lcpMissingPassphrase"
    )
    throw BorrowSubtaskFailed()
  }

  /**
   * Check that we actually have the required DRM support.
   */

  private fun checkDRMSupport(
    context: BorrowContextType
  ) {
    context.taskRecorder.beginNewStep("Checking for LCP support...")
    if (context.lcpService == null) {
      context.taskRecorder.currentStepFailed(
        message = "This build of the application does not support LCP DRM.",
        errorCode = lcpNotSupported
      )
      throw BorrowSubtaskFailed()
    }
  }

  private fun fulfill(
    context: BorrowContextType,
    licenseBytes: ByteArray,
    passphrase: String
  ) {
    context.taskRecorder.beginNewStep("Fulfilling book...")
    context.checkCancelled()

    val fulfillmentMimeType = context.opdsAcquisitionPath.asMIMETypes().last()

    // An LCP download is always actually a zip file, whether it's an epub, audio book, or pdf.
    // TrueVFS will by default detect files with the .zip extension as ZIP archives that can be
    // mounted, so the extension is important to install the license.

    val temporaryFile = context.temporaryFile("zip")

    try {
      val license = LicenseDocument(licenseBytes)
      val link = license.link(LicenseDocument.Rel.publication)
      val url = link?.url
        ?: throw LcpException.Parsing.Url(rel = LicenseDocument.Rel.publication.rawValue)

      val downloadRequest =
        BorrowHTTP.createDownloadRequest(
          context = context,
          target = url.toURI(),
          outputFile = temporaryFile,
          requestModifier = { properties ->
            properties.copy(
              authorization = null
            )
          },
          expectedTypes = hashSetOf(
            fulfillmentMimeType,
            // Sometimes fulfillment servers will set the content type to generic zip or octet
            // stream, so these are acceptable too.
            StandardFormatNames.genericZIPFiles,
            MIMECompatibility.applicationOctetStream
          )
        )

      when (val result = LSHTTPDownloads.download(downloadRequest)) {
        DownloadCancelled ->
          throw BorrowSubtaskCancelled()
        is DownloadFailedServer ->
          throw BorrowHTTP.onDownloadFailedServer(context, result)
        is DownloadFailedUnacceptableMIME ->
          throw BorrowSubtaskFailed()
        is DownloadFailedExceptionally ->
          throw BorrowHTTP.onDownloadFailedExceptionally(context, result)
        is DownloadCompletedSuccessfully -> {
          this.installLicense(context, fulfillmentMimeType, temporaryFile, licenseBytes)
          this.saveFulfilledBook(context, temporaryFile, passphrase)

          context.bookDownloadSucceeded()
        }
      }
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    } catch (e: Exception) {
      context.taskRecorder.currentStepFailed(
        message = "LCP fulfillment error: ${e.message}",
        errorCode = BorrowErrorCodes.lcpFulfillmentFailed,
        exception = e
      )
      throw BorrowSubtaskFailed()
    } finally {
      temporaryFile.delete()
    }

    /*
     * LCP is a special case in the sense that it supersedes any acquisition
     * path elements that might follow this one. We mark this subtask as having halted
     * early.
     */

    throw BorrowSubtaskHaltedEarly()
  }

  /**
   * Install the license into the fulfilled book, which is in all cases a zip file. This is done
   * efficiently, without fully rewriting the potentially large file.
   */

  private fun installLicense(
    context: BorrowContextType,
    bookType: MIMEType,
    bookFile: File,
    licenseBytes: ByteArray
  ) {
    context.taskRecorder.beginNewStep("Installing license...")

    val pathInZIP = when (bookType) {
      StandardFormatNames.genericEPUBFiles -> "META-INF/license.lcpl"
      else -> "license.lcpl"
    }

    // Use TrueVFS to mount the zip file, and copy in the license. The GROW option ensures that
    // adding the new entry to the zip only appends the file to the end of the archive, without
    // rewriting the entire file.

    TConfig.current().setAccessPreference(FsAccessOption.GROW, true)

    TFile.cp(
      ByteArrayInputStream(licenseBytes),
      TFile(bookFile, pathInZIP)
    )

    TVFS.umount()

    context.taskRecorder.currentStepSucceeded("License installed.")
  }

  private fun saveFulfilledBook(
    context: BorrowContextType,
    bookFile: File,
    passphrase: String
  ) {
    context.taskRecorder.beginNewStep("Saving fulfilled book...")

    val formatHandle = this.findFormatHandle(context)
    formatHandle.setDRMKind(BookDRMKind.LCP)

    val drmHandle = formatHandle.drmInformationHandle as BookDRMInformationHandleLCP
    drmHandle.setHashedPassphrase(passphrase)

    when (formatHandle) {
      is BookDatabaseEntryFormatHandleEPUB -> {
        formatHandle.copyInBook(bookFile)
      }
      is BookDatabaseEntryFormatHandleAudioBook -> {
        formatHandle.moveInBook(bookFile)
      }
      is BookDatabaseEntryFormatHandlePDF ->
        formatHandle.copyInBook(bookFile)
    }

    context.taskRecorder.currentStepSucceeded("Saved book.")
  }

  /**
   * Determine the actual book format we're aiming for at the end of the acquisition path.
   */

  private fun findFormatHandle(
    context: BorrowContextType
  ): BookDatabaseEntryFormatHandle {
    val eventualType = context.opdsAcquisitionPath.asMIMETypes().last()
    val formatHandle = context.bookDatabaseEntry.findFormatHandleForContentType(eventualType)
    if (formatHandle == null) {
      context.taskRecorder.currentStepFailed(
        message = "No format handle available for ${eventualType.fullType}",
        errorCode = BorrowErrorCodes.noFormatHandle
      )
      throw BorrowSubtaskFailed()
    }
    return formatHandle
  }
}
