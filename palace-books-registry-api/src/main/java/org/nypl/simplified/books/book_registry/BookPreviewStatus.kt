package org.nypl.simplified.books.book_registry

import one.irradia.mime.api.MIMEType
import java.io.File
import java.net.URL

sealed class BookPreviewStatus {

  object None : BookPreviewStatus()

  open class HasPreview : BookPreviewStatus() {

    data class Downloading(
      val bytesPerSecond: Long?,
      val currentTotalBytes: Long?,
      val expectedTotalBytes: Long?,
      val detailMessage: String
    ) : HasPreview()

    class DownloadFailed : HasPreview()

    sealed class Ready : HasPreview() {

      data class Embedded(val url: URL) : Ready()

      data class BookPreview(val file: File, val mimeType: MIMEType) : Ready()

      data class AudiobookPreview(val file: File) : Ready()
    }
  }
}
