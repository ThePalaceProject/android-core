package org.nypl.simplified.books.borrowing.internal

import kotlinx.coroutines.runBlocking
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
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
import org.readium.r2.lcp.LcpService
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
      return MIMECompatibility.isCompatibleStrictWithoutAttributes(type, StandardFormatNames.lcpLicenseFiles)
    }
  }

  override fun execute(context: BorrowContextType) {
    try {
      this.checkDRMSupport(context)

      val passphrase = this.findPassphrase(context)

      context.taskRecorder.beginNewStep("Downloading LCP license…")
      context.bookDownloadIsRunning(
        "Downloading...",
        receivedSize = 0L,
        expectedSize = 100L,
        bytesPerSecond = 1L
      )

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
          LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled ->
            throw BorrowSubtaskCancelled()
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer ->
            throw BorrowHTTP.onDownloadFailedServer(context, result)
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME ->
            throw BorrowSubtaskFailed()
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally ->
            throw BorrowHTTP.onDownloadFailedExceptionally(context, result)
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully -> {
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

    val request =
      context.httpClient.newRequest(loansURI)
        .setAuthorization(BorrowHTTP.authorizationOf(context.account))
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK ->
          this.findPassphraseHandleOK(context, loansURI, status)
        is LSHTTPResponseStatus.Responded.Error ->
          this.findPassphraseHandleError(context, status)
        is LSHTTPResponseStatus.Failed ->
          this.findPassphraseHandleFailure(context, status)
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
    context.bookDownloadIsRunning("Downloading...")
    context.taskRecorder.beginNewStep("Fulfilling book...")
    context.checkCancelled()

    val lcpService = context.lcpService!!

    val result =
      runBlocking {
        lcpService.acquirePublication(
          lcpl = licenseBytes,
          onProgress = { percent ->
            context.bookDownloadIsRunning(
              message = "Downloading…",
              receivedSize = (percent * 100).toLong(),
              expectedSize = 100,
              bytesPerSecond = null
            )
          }
        )
      }

    try {
      val publication = result.getOrThrow()
      this.saveFulfilledBook(context, publication, passphrase)
    } catch (e: Exception) {
      context.taskRecorder.currentStepFailed(
        message = "LCP fulfillment error: ${e.message}",
        errorCode = BorrowErrorCodes.lcpFulfillmentFailed,
        exception = e
      )
      throw BorrowSubtaskFailed()
    }

    throw BorrowSubtaskHaltedEarly()
  }

  private fun saveFulfilledBook(
    context: BorrowContextType,
    publication: LcpService.AcquiredPublication,
    passphrase: String
  ) {
    context.taskRecorder.beginNewStep("Saving fulfilled book...")
    val formatHandle = context.bookDatabaseEntry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
    checkNotNull(formatHandle) {
      "A format handle for EPUB must be available."
    }

    formatHandle.setDRMKind(BookDRMKind.LCP)
    val drmHandle = formatHandle.drmInformationHandle as BookDRMInformationHandleLCP
    drmHandle.setHashedPassphrase(passphrase)

    formatHandle.copyInBook(publication.localFile)
    context.taskRecorder.currentStepSucceeded("Saved book.")
    context.bookDownloadSucceeded()
  }
}
