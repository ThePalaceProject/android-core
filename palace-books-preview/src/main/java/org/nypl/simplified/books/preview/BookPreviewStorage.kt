package org.nypl.simplified.books.preview

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.files.FileUtilities
import java.io.File

data class BookPreviewStorage(
  val directory: File
) {

  private val bookPreviewFile: File =
    File(directory, "book-preview.epub")

  private val audiobookMp3PreviewFile: File =
    File(directory, "audiobook-preview.mp3")

  private val audiobookWmaPreviewFile: File =
    File(directory, "audiobook-preview.wma")

  fun saveBookPreview(file: File, onBookSuccessfullySaved: (File) -> Unit) {
    if (file.isDirectory) {
      DirectoryUtilities.directoryCopy(file, bookPreviewFile)
    } else {
      FileUtilities.fileCopy(file, bookPreviewFile)
    }

    onBookSuccessfullySaved(bookPreviewFile)
  }

  fun saveAudiobookPreview(
    file: File,
    mimeType: MIMEType,
    onBookSuccessfullySaved: (File) -> Unit
  ) {
    val previewFile = when (mimeType) {
      StandardFormatNames.mpegAudioBooks -> {
        audiobookMp3PreviewFile
      }
      StandardFormatNames.wmaAudioBooks -> {
        audiobookWmaPreviewFile
      }
      else -> {
        throw Exception("Unsupported MIME type: $mimeType")
      }
    }

    if (file.isDirectory) {
      DirectoryUtilities.directoryCopy(file, previewFile)
    } else {
      FileUtilities.fileCopy(file, previewFile)
    }

    onBookSuccessfullySaved(previewFile)
  }
}
