package org.nypl.simplified.books.borrowing.internal

import com.io7m.junreachable.UnreachableCodeException
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.AllowRedirects.ALLOW_UNSAFE_REDIRECTS
import org.librarysimplified.http.api.LSHTTPRequestProperties
import org.librarysimplified.http.downloads.LSHTTPDownloadRequest
import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.librarysimplified.http.downloads.LSHTTPDownloadState.DownloadReceiving
import org.librarysimplified.http.downloads.LSHTTPDownloadState.DownloadStarted
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import java.io.File
import java.net.URI

/**
 * Convenience functions over HTTP.
 */

object BorrowHTTP {

  /**
   * Create a download request for the given URI, downloading content to the given output file.
   * Events will be delivered to the given borrow context.
   */

  fun createDownloadRequest(
    context: BorrowContextType,
    target: URI,
    outputFile: File,
    requestModifier: ((LSHTTPRequestProperties) -> LSHTTPRequestProperties)? = null,
    expectedTypes: Set<MIMEType> = hashSetOf(context.currentAcquisitionPathElement.mimeType)
  ): LSHTTPDownloadRequest {
    val credentials = context.account.loginState.credentials

    val auth =
      AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

    val request =
      context.httpClient.newRequest(target)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .allowRedirects(ALLOW_UNSAFE_REDIRECTS)
        .apply {
          if (requestModifier != null) {
            setRequestModifier(requestModifier)
          }
        }
        .build()

    return LSHTTPDownloadRequest(
      request = request,
      outputFile = outputFile,
      onEvent = {
        this.onDownloadProgressEvent(context, it)
      },
      isMIMETypeAcceptable = {
        this.isMimeTypeAcceptable(context, expectedTypes, it)
      },
      isCancelled = {
        context.isCancelled
      },
      clock = context.clock
    )
  }

  /**
   * Check to see if the given MIME type is "acceptable" according to the current borrowing
   * context. If the type is not acceptable, the current task recorder step will be marked as
   * failed with an appropriate error message.
   *
   * @return `true` if the received MIME type is acceptable
   */

  fun isMimeTypeAcceptable(
    context: BorrowContextType,
    receivedType: MIMEType
  ): Boolean {
    return isMimeTypeAcceptable(
      context,
      hashSetOf(context.currentAcquisitionPathElement.mimeType),
      receivedType
    )
  }

  /**
   * Check to see if the given MIME type is "acceptable", given a set of expected types. If the type
   * is not acceptable, the current task recorder step will be marked as failed with an appropriate
   * error message.
   *
   * @return `true` if the received MIME type is acceptable
   */

  fun isMimeTypeAcceptable(
    context: BorrowContextType,
    expectedTypes: Set<MIMEType>,
    receivedType: MIMEType
  ): Boolean {
    return if (
      expectedTypes.any { MIMECompatibility.isCompatibleLax(receivedType, it) }
    ) {
      true
    } else {
      val expectedTypesDesc = expectedTypes.map { it.fullType }.joinToString(" or ")
      context.taskRecorder.currentStepFailed(
        message = "The server returned an incompatible context type: We wanted something compatible with $expectedTypesDesc but received ${receivedType.fullType}.",
        errorCode = BorrowErrorCodes.httpContentTypeIncompatible,
        extraMessages = listOf()
      )
      false
    }
  }

  /**
   * Record a server error to the task recorder.
   */

  fun onDownloadFailedServer(
    context: BorrowContextType,
    result: DownloadFailedServer
  ): BorrowSubtaskFailed {
    val status = result.responseStatus
    context.taskRecorder.addAttributes(status.properties.problemReport?.toMap() ?: emptyMap())
    context.taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
      errorCode = BorrowErrorCodes.httpRequestFailed,
      exception = null,
      extraMessages = listOf()
    )
    return BorrowSubtaskFailed()
  }

  /**
   * Record a download exception to the task recorder.
   */

  fun onDownloadFailedExceptionally(
    context: BorrowContextType,
    result: DownloadFailedExceptionally
  ): BorrowSubtaskFailed {
    context.taskRecorder.currentStepFailed(
      message = result.exception.message ?: "Exception raised during connection attempt.",
      errorCode = BorrowErrorCodes.httpConnectionFailed,
      exception = result.exception,
      extraMessages = listOf()
    )
    return BorrowSubtaskFailed()
  }

  /**
   * A default handler for DownloadFailedUnacceptableMIME that just throws BorrowSubtaskFailed.
   */

  fun onDownloadFailedUnacceptableMimeDefault(
    context: BorrowContextType,
    result: DownloadFailedUnacceptableMIME
  ) {
    throw BorrowSubtaskFailed()
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

  fun downloadingMessage(
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
   * Download the file indicated by the given borrowing context.
   *
   * @param context The borrowing context.
   * @param onDownloadFailedUnacceptableMIME A handler to be called if the downloaded file has a
   * content type that is not acceptable according to the borrowing context. If not provided, a
   * BorrowSubtaskFailed exception is thrown.
   */

  fun download(
    context: BorrowContextType,
    onDownloadFailedUnacceptableMIME: (BorrowContextType, DownloadFailedUnacceptableMIME) -> Unit =
      this::onDownloadFailedUnacceptableMimeDefault,
    requestModifier: ((LSHTTPRequestProperties) -> LSHTTPRequestProperties)? = null
  ) {
    return try {
      val currentURI = context.currentURICheck()
      context.logDebug("downloading {}", currentURI)
      context.taskRecorder.beginNewStep("Downloading $currentURI...")
      context.taskRecorder.addAttribute("URI", currentURI.toString())

      val temporaryFile = context.temporaryFile()

      try {
        val downloadRequest =
          createDownloadRequest(
            context = context,
            target = currentURI,
            outputFile = temporaryFile,
            requestModifier = requestModifier
          )

        when (val result = LSHTTPDownloads.download(downloadRequest)) {
          DownloadCancelled ->
            throw BorrowSubtaskException.BorrowSubtaskCancelled()
          is DownloadFailedServer ->
            throw onDownloadFailedServer(context, result)
          is DownloadFailedUnacceptableMIME ->
            onDownloadFailedUnacceptableMIME(context, result)
          is DownloadFailedExceptionally ->
            throw onDownloadFailedExceptionally(context, result)
          is DownloadCompletedSuccessfully ->
            this.saveDownloadedContent(context, temporaryFile)
        }
      } finally {
        temporaryFile.delete()
      }
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private fun saveDownloadedContent(
    context: BorrowContextType,
    temporaryFile: File
  ) {
    context.taskRecorder.beginNewStep("Saving book...")

    val formatHandle =
      context.bookDatabaseEntry.findFormatHandleForContentType(
        context.currentAcquisitionPathElement.mimeType
      )

    return when (formatHandle) {
      is BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB -> {
        formatHandle.copyInBook(temporaryFile)
        context.bookDownloadSucceeded()
      }
      is BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF -> {
        formatHandle.copyInBook(temporaryFile)
        context.bookDownloadSucceeded()
      }
      is BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook,
      null ->
        throw UnreachableCodeException()
    }
  }
}
