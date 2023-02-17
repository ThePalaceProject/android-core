package org.nypl.simplified.books.preview

import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStepResolution
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

class BookPreviewTask(
  private val bookPreviewRegistry: BookPreviewRegistryType,
  private val bookPreviewRequirements: BookPreviewRequirements,
  private val feedEntry: OPDSAcquisitionFeedEntry,
  private val format: BookFormats.BookFormatDefinition?
) {

  private lateinit var taskRecorder: TaskRecorderType

  private val logger =
    LoggerFactory.getLogger(BookPreviewTask::class.java)

  private fun debug(message: String, vararg arguments: Any?) {
    this.logger.debug("[{}] $message", this.feedEntry.id, *arguments)
  }

  private fun error(message: String, vararg arguments: Any?) {
    this.logger.error("[{}] $message", this.feedEntry.id, *arguments)
  }

  private fun warn(message: String, vararg arguments: Any?) {
    this.logger.warn("[{}] $message", this.feedEntry.id, *arguments)
  }

  private fun messageOrName(e: Throwable): String {
    return e.message ?: e.javaClass.name
  }

  private fun handleBookPreviewDownload(
    onPreviewFileReady: (File) -> Unit,
    previewAcquisition: OPDSPreviewAcquisition
  ): TaskResult<*> {
    val taskName = "BookPreviewDownloadTask"
    val step = this.taskRecorder.beginNewStep("Executing subtask $taskName...")

    return try {
      BookPreviewDownload(
        parameters = BookPreviewParameters(
          clock = bookPreviewRequirements.clock,
          feedEntry = feedEntry,
          format = format!!,
          httpClient = bookPreviewRequirements.httpClient,
          mimeType = previewAcquisition.type,
          onPreviewDownloadFailed = this::onPreviewDownloadFailed,
          onPreviewDownloadUpdated = this::onPreviewDownloadUpdated,
          onPreviewFileReady = onPreviewFileReady,
          previewAcquisition = previewAcquisition,
          taskRecorder = taskRecorder,
          temporaryDirectory = bookPreviewRegistry.getPreviewDownloadDirectory()
        )
      ).execute()

      step.resolution =
        TaskStepResolution.TaskStepSucceeded("Executed subtask $taskName successfully.")

      this.taskRecorder.finishSuccess(Unit)
    } catch (e: Exception) {
      step.resolution = TaskStepResolution.TaskStepFailed(
        message = "Subtask $taskName raised an unexpected exception",
        exception = e,
        errorCode = "subtaskFailed"
      )
      this.taskRecorder.finishFailure<Unit>()
    }
  }

  private fun onPreviewDownloadFailed() {
    bookPreviewRegistry.updatePreviewStatus(
      BookPreviewStatus.HasPreview.DownloadFailed()
    )
  }

  private fun onPreviewDownloadUpdated(
    message: String,
    receivedSize: Long?,
    expectedSize: Long?,
    bytesPerSecond: Long?
  ) {
    this.debug("downloading: {} {} {}", expectedSize, receivedSize, bytesPerSecond)

    bookPreviewRegistry.updatePreviewStatus(
      BookPreviewStatus.HasPreview.Downloading(
        bytesPerSecond = bytesPerSecond,
        currentTotalBytes = receivedSize,
        expectedTotalBytes = expectedSize,
        detailMessage = message
      )
    )
  }

  private fun handlePreviewDownload(previewAcquisition: OPDSPreviewAcquisition): TaskResult<*> {
    return when (format) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO,
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB -> {
        handleBookPreviewDownload(
          onPreviewFileReady = { file ->
            bookPreviewRegistry.updatePreviewStatus(
              if (format == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
                BookPreviewStatus.HasPreview.Ready.AudiobookPreview(
                  file = file
                )
              } else {
                BookPreviewStatus.HasPreview.Ready.BookPreview(
                  file = file,
                  mimeType = previewAcquisition.type
                )
              }
            )
          },
          previewAcquisition = previewAcquisition
        )
      }
      else -> {
        this.taskRecorder.currentStepFailed(
          "Non supported book format.",
          BookPreviewErrorCodes.nonSupportedBookFormat
        )
        this.taskRecorder.finishFailure<Unit>()
      }
    }
  }

  private fun startBookPreviewHandling(): TaskResult<*> {
    this.taskRecorder.addAttribute("Book", feedEntry.title)
    this.taskRecorder.addAttribute("Author", feedEntry.authorsCommaSeparated)

    val previewAcquisition = pickPreviewAcquisition()

    // the preview acquisition is a 'text/html'
    return if (previewAcquisition.type == StandardFormatNames.textHtmlBook) {
      bookPreviewRegistry.updatePreviewStatus(
        BookPreviewStatus.HasPreview.Ready.Embedded(
          url = URL(previewAcquisition.uri.toString())
        )
      )
      this.taskRecorder.finishSuccess(Unit)
    } else {
      handlePreviewDownload(
        previewAcquisition = previewAcquisition
      )
    }
  }

  private fun pickPreviewAcquisition(): OPDSPreviewAcquisition {
    this.taskRecorder.beginNewStep("Planning the preview operation…")

    if (feedEntry.previewAcquisitions.isNullOrEmpty()) {
      this.bookPreviewRegistry.updatePreviewStatus(
        BookPreviewStatus.None
      )
      this.taskRecorder.currentStepFailed(
        "No preview acquisitions.",
        BookPreviewErrorCodes.noPreviewAcquisitions
      )
      this.taskRecorder.finishFailure<Unit>()
      throw BookPreviewException(null)
    }

    val previewAcquisition = BookPreviewAcquisitions.pickBestPreviewAcquisition(feedEntry)
    if (previewAcquisition == null) {
      this.taskRecorder.currentStepFailed(
        "No supported preview acquisitions.",
        BookPreviewErrorCodes.noSupportedPreviewAcquisitions
      )
      throw BookPreviewException(null)
    }

    this.taskRecorder.currentStepSucceeded("Selected a preview acquisition.")
    return previewAcquisition
  }

  fun execute(): TaskResult<*> {
    this.taskRecorder = TaskRecorder.create()
    this.debug("starting")

    return try {
      return startBookPreviewHandling()
    } catch (e: BookPreviewException) {
      this.warn("handled: ", e)
      this.taskRecorder.finishFailure<Unit>()
    } catch (e: Throwable) {
      this.error("unhandled exception during book preview handling: ", e)
      this.taskRecorder.currentStepFailedAppending(this.messageOrName(e), "unexpected exception", e)
      this.taskRecorder.finishFailure<Unit>()
    }
  }
}
