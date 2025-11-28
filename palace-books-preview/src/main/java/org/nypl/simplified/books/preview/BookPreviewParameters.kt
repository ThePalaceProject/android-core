package org.nypl.simplified.books.preview

import one.irradia.mime.api.MIMEType
import org.joda.time.Instant
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import java.io.File
import java.io.IOException
import java.util.UUID

data class BookPreviewParameters(
  val clock: () -> Instant,
  val feedEntry: OPDSAcquisitionFeedEntry,
  val format: BookFormats.BookFormatDefinition,
  val httpClient: LSHTTPClientType,
  val mimeType: MIMEType,
  val onPreviewDownloadFailed: () -> Unit,
  val onPreviewDownloadUpdated: (String, Long?, Long?, Long?) -> Unit,
  val onPreviewFileReady: (File) -> Unit,
  val previewAcquisition: OPDSPreviewAcquisition,
  val taskRecorder: TaskRecorderType,
  val temporaryDirectory: File
) {

  private lateinit var temporaryFile: File

  /**
   * A flag that indicates a preview task has been cancelled. Subtasks should take care to
   * observe this flag during long-running operations in order to support cancellation.
   *
   * @return `true` if the preview task has been cancelled
   */

  var isCancelled: Boolean = false

  fun getTemporaryFile(): File {
    if (!::temporaryFile.isInitialized) {
      val extension = ".tmp"
      this.temporaryDirectory.mkdirs()
      for (i in 0..100) {
        val file = File(this.temporaryDirectory, "${UUID.randomUUID()}$extension")
        if (!file.exists()) {
          temporaryFile = file
          return temporaryFile
        }
      }
      throw IOException("Could not create a temporary file within 100 attempts!")
    }

    return temporaryFile
  }
}
