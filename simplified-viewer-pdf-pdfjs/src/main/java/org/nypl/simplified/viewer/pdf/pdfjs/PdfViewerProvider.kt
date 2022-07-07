package org.nypl.simplified.viewer.pdf.pdfjs

import android.app.Activity
import one.irradia.mime.api.MIMEType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory

class PdfViewerProvider : ViewerProviderType {

  private val logger =
    LoggerFactory.getLogger(PdfViewerProvider::class.java)

  override val name: String =
    "org.nypl.simplified.viewer.pdf.pdfjs.PdfViewerProvider"

  override fun canSupport(
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat
  ): Boolean {
    return when (format) {
      is BookFormat.BookFormatEPUB,
      is BookFormat.BookFormatAudioBook -> {
        logger.debug("the PDF viewer can only open PDF files!")
        false
      }
      is BookFormat.BookFormatPDF -> {
        preferences.flags["enablePDFJSReader"] == true
      }
    }
  }

  override fun canPotentiallySupportType(type: MIMEType): Boolean {
    return type == StandardFormatNames.genericPDFFiles
  }

  override fun open(
    activity: Activity,
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat
  ) {
    val formatPDF = format as BookFormat.BookFormatPDF

    PdfReaderActivity.startActivity(
      from = activity,
      parameters = PdfReaderParameters(
        accountId = book.account,
        documentTitle = book.entry.title,
        pdfFile = formatPDF.file!!,
        id = book.id,
        drmInfo = formatPDF.drmInformation
      )
    )
  }
}
