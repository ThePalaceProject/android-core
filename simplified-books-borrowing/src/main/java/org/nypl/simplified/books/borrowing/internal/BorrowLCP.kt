package org.nypl.simplified.books.borrowing.internal

import kotlinx.coroutines.runBlocking
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.lcpNotSupported
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
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
      context.bookDownloadIsRunning(
        "Downloading...",
        receivedSize = 0L,
        expectedSize = 100L,
        bytesPerSecond = 1L
      )

      this.checkDRMSupport(context)

      context.taskRecorder.beginNewStep("Downloading LCP license...")

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
            throw BorrowSubtaskException.BorrowSubtaskCancelled()
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer ->
            throw BorrowHTTP.onDownloadFailedServer(context, result)
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME ->
            throw BorrowSubtaskFailed()
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally ->
            throw BorrowHTTP.onDownloadFailedExceptionally(context, result)
          is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully -> {
            this.fulfill(context, temporaryFile.readBytes())
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
    licenseBytes: ByteArray
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
          })
      }

    try {
      val publication = result.getOrThrow()
      saveFulfilledBook(context, publication)
      throw BorrowSubtaskException.BorrowSubtaskHaltedEarly()
    } catch (e: Exception) {
      context.taskRecorder.currentStepFailed(
        message = "LCP fulfillment error: ${e.message}",
        errorCode = BorrowErrorCodes.lcpFulfillmentFailed,
        exception = e
      )
      throw BorrowSubtaskFailed()
    }
  }

  private fun saveFulfilledBook(
    context: BorrowContextType,
    publication: LcpService.AcquiredPublication
  ) {
    context.taskRecorder.beginNewStep("Saving fulfilled book...")
    val formatHandle = context.bookDatabaseEntry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
    checkNotNull(formatHandle) {
      "A format handle for EPUB must be available."
    }

    formatHandle.setDRMKind(BookDRMKind.LCP)
    formatHandle.copyInBook(publication.localFile)
    context.taskRecorder.currentStepSucceeded("Saved book.")
    context.bookDownloadSucceeded()
  }
}
