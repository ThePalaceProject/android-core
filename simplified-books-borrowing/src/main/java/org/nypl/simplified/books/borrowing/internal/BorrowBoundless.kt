package org.nypl.simplified.books.borrowing.internal

import com.io7m.junreachable.UnreachableCodeException
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.librarysimplified.http.downloads.LSHTTPDownloadState.DownloadReceiving
import org.librarysimplified.http.downloads.LSHTTPDownloadState.DownloadStarted
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.nypl.drm.core.BoundlessCMTemplatedLink
import org.nypl.drm.core.BoundlessFulfilledCMEPUB
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.drm.core.DRMTaskResult.DRMTaskCancelled
import org.nypl.drm.core.DRMTaskResult.DRMTaskFailure
import org.nypl.drm.core.DRMTaskResult.DRMTaskSuccess
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.BookDRMInformationHandleBoundless
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.boundlessNotSupported
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.links.Link

class BorrowBoundless private constructor() : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "Boundless Download"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowBoundless()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: Link?,
      account: AccountReadableType?,
      remaining: List<MIMEType>
    ): Boolean {
      return MIMECompatibility.isCompatibleStrictWithoutAttributes(
        type, StandardFormatNames.boundlessLicenseFiles
      )
    }
  }

  override fun execute(
    context: BorrowContextType
  ) {
    val boundless =
      this.checkDRMSupport(context)

    val outputFile =
      context.temporaryFile(extension = ".epub")
    val outputLicenseFile =
      context.temporaryFile(extension = ".json")

    val credentials =
      context.account.loginState.credentials
    val authorization =
      if (credentials != null) {
        AccountAuthenticatedHTTP.createAuthorization(credentials)
      } else {
        null
      }

    /*
     * We require that the CM link be a templated link, and we handle it by stripping off the
     * query and parameters. The Boundless library will substitute in its own parameters as
     * necessary.
     */

    val templatedLink =
      context.currentLinkCheck()
        .toTemplated()
    val link =
      BoundlessCMTemplatedLink(templatedLink.href)

    val result = boundless.fulfillEPUB(
      httpClient = context.httpClient,
      link = link,
      credentials = authorization,
      outputFile = outputFile,
      outputLicenseFile = outputLicenseFile,
      isCancelled = {
        context.isCancelled
      },
      onDownloadEvent = { event ->
        this.onDownloadProgressEvent(context, event)
      }
    )

    return when (result) {
      is DRMTaskCancelled ->
        throw BorrowSubtaskException.BorrowSubtaskCancelled()
      is DRMTaskFailure ->
        throw this.drmFailed(context, result)
      is DRMTaskSuccess -> {
        this.saveFulfilledBook(context, result.value)
        throw BorrowSubtaskException.BorrowSubtaskHaltedEarly()
      }
    }
  }

  private fun drmFailed(
    context: BorrowContextType,
    result: DRMTaskFailure<BoundlessFulfilledCMEPUB>
  ): BorrowSubtaskException {
    for (entry in result.attributes) {
      context.taskRecorder.addAttribute(entry.key, entry.value)
    }
    for (step in result.steps) {
      context.taskRecorder.beginNewStep(step.message)
      val failure = step.failed
      if (failure != null) {
        context.taskRecorder.currentStepFailed(
          message = failure.message ?: failure.javaClass.name,
          errorCode = "drm-exception",
          exception = failure,
          extraMessages = listOf()
        )
      }
    }
    return BorrowSubtaskFailed()
  }

  private fun saveFulfilledBook(
    context: BorrowContextType,
    fulfilledItems: BoundlessFulfilledCMEPUB
  ) {
    context.taskRecorder.beginNewStep("Saving fulfilled book...")

    val formatHandle = this.findFormatHandle(context)
    formatHandle.setDRMKind(BookDRMKind.BOUNDLESS)

    val drmHandle = formatHandle.drmInformationHandle as BookDRMInformationHandleBoundless
    drmHandle.copyInBoundlessLicense(fulfilledItems.licenseFile)

    when (formatHandle) {
      is BookDatabaseEntryFormatHandleEPUB -> {
        formatHandle.copyInBook(fulfilledItems.epubFile)
      }

      is BookDatabaseEntryFormatHandleAudioBook,
      is BookDatabaseEntryFormatHandlePDF ->
        throw UnreachableCodeException()
    }

    fulfilledItems.epubFile.delete()
    fulfilledItems.licenseFile.delete()
    context.taskRecorder.currentStepSucceeded("Saved book.")
  }

  private fun checkDRMSupport(
    context: BorrowContextType
  ): BoundlessServiceType {
    context.taskRecorder.beginNewStep("Checking for Boundless DRM support...")
    val boundless = context.boundlessService
    if (boundless == null) {
      context.taskRecorder.currentStepFailed(
        message = "This build of the application does not support Boundless DRM.",
        errorCode = boundlessNotSupported,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }

    context.taskRecorder.currentStepSucceeded("Boundless DRM is supported.")
    return boundless
  }

  private fun onDownloadProgressEvent(
    context: BorrowContextType,
    event: LSHTTPDownloadState
  ) {
    when (event) {
      is DownloadReceiving -> {
        context.account.updateBasicTokenCredentials(event.accessToken)

        context.bookDownloadIsRunning(
          message = this.downloadingMessage(
            expectedSize = event.expectedSize,
            currentSize = event.receivedSize,
            perSecond = event.bytesPerSecond
          ),
          receivedSize = event.receivedSize,
          expectedSize = event.expectedSize,
          bytesPerSecond = event.bytesPerSecond
        )
      }

      DownloadStarted,
      DownloadCancelled,
      is DownloadFailedServer,
      is DownloadFailedUnacceptableMIME,
      is DownloadFailedExceptionally,
      is DownloadCompletedSuccessfully -> {
        // Don't care
      }
    }
  }

  private fun downloadingMessage(
    expectedSize: Long?,
    currentSize: Long,
    perSecond: Long
  ): String {
    return if (expectedSize == null) {
      "Downloading..."
    } else {
      "Downloading $currentSize / $expectedSize ($perSecond)..."
    }
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
        errorCode = BorrowErrorCodes.noFormatHandle,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }
    return formatHandle
  }
}
