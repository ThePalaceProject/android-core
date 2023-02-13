package org.nypl.simplified.books.preview

import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.downloads.LSHTTPDownloadRequest
import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.taskrecorder.api.TaskRecorderType

class BookPreviewHttp {

  private fun onDownloadProgressEvent(
    parameters: BookPreviewParameters,
    event: LSHTTPDownloadState
  ) {
    when (event) {
      is LSHTTPDownloadState.DownloadReceiving -> {
        parameters.onPreviewDownloadUpdated(
          this.createDownloadingMessage(
            expectedSize = event.expectedSize,
            currentSize = event.receivedSize,
            perSecond = event.bytesPerSecond
          ),
          event.receivedSize,
          event.expectedSize,
          event.bytesPerSecond
        )
      }

      LSHTTPDownloadState.DownloadStarted,
      LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled,
      is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer,
      is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME,
      is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally,
      is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully -> {
        // do nothing
      }
    }
  }

  private fun createDownloadRequest(
    parameters: BookPreviewParameters
  ): LSHTTPDownloadRequest {
    val uri = parameters.previewAcquisition.uri

    val request = parameters.httpClient.newRequest(uri)
      .allowRedirects(LSHTTPRequestBuilderType.AllowRedirects.ALLOW_UNSAFE_REDIRECTS)
      .build()

    return LSHTTPDownloadRequest(
      request = request,
      outputFile = parameters.getTemporaryFile(),
      onEvent = { event ->
        onDownloadProgressEvent(parameters, event)
      },
      isMIMETypeAcceptable = {
        true
      },
      isCancelled = {
        parameters.isCancelled
      },
      clock = parameters.clock
    )
  }

  private fun saveDownloadedContent(
    parameters: BookPreviewParameters
  ) {
    parameters.taskRecorder.beginNewStep("Saving book...")

    val storage = BookPreviewStorage(
      parameters.temporaryDirectory
    )

    when (parameters.format) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO -> {
        storage.saveAudiobookPreview(
          file = parameters.getTemporaryFile(),
          mimeType = parameters.mimeType,
          onBookSuccessfullySaved = {
            parameters.onPreviewFileReady(it)
          }
        )
      }
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB -> {
        storage.saveBookPreview(
          file = parameters.getTemporaryFile(),
          onBookSuccessfullySaved = {
            parameters.onPreviewFileReady(it)
          }
        )
      }
      else -> {
        throw Exception("Unsupported book preview")
      }
    }
  }

  private fun createDownloadingMessage(
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

  private fun handleHttpError(
    taskRecorder: TaskRecorderType,
    result: LSHTTPDownloadState.LSHTTPDownloadResult
  ) {
    val status = result.responseStatus
    taskRecorder.addAttributes(status?.properties?.problemReport?.toMap() ?: emptyMap())
    taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status?.properties?.originalStatus} ${status?.properties?.message}",
      errorCode = "httpRequestFailed",
      exception = null
    )
  }

  fun download(parameters: BookPreviewParameters) {
    val uri = parameters.previewAcquisition.uri
    parameters.taskRecorder.beginNewStep("Downloading $uri...")
    parameters.taskRecorder.addAttribute("URI", uri.toString())

    try {
      val downloadRequest = createDownloadRequest(
        parameters = parameters
      )

      when (val result = LSHTTPDownloads.download(downloadRequest)) {
        is LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully -> {
          this.saveDownloadedContent(parameters)
        }
        else -> {
          handleHttpError(
            taskRecorder = parameters.taskRecorder,
            result = result
          )
          throw Exception()
        }
      }
    } catch (e: Exception) {
      parameters.onPreviewDownloadFailed()
      throw e
    } finally {
      parameters.getTemporaryFile().delete()
    }
  }
}
