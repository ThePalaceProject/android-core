package org.nypl.simplified.books.preview

class BookPreviewDownload(
  private val parameters: BookPreviewParameters
) {

  fun execute() {
    parameters.taskRecorder.beginNewStep("Downloading directly...")
    parameters.onPreviewDownloadUpdated(
      "Requesting download...",
      0,
      null,
      0
    )

    BookPreviewHttp().download(
      parameters = parameters
    )
  }
}
