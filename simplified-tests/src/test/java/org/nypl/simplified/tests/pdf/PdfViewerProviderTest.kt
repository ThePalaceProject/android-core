package org.nypl.simplified.tests.pdf

import one.irradia.mime.vanilla.MIMEParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.viewer.pdf.pdfjs.PdfViewerProvider
import org.nypl.simplified.viewer.spi.ViewerPreferences

class PdfViewerProviderTest {

  @Test
  fun doesNotSupportPdfBooksWhenDisabled() {
    val preferences = ViewerPreferences(
      // by setting the old PDF reader setting to true, we're disabling the PDF-JS reader
      flags = mapOf(
        "enableOldPDFReader" to true
      )
    )

    val book = Mockito.mock(Book::class.java)
    val format = Mockito.mock(BookFormat.BookFormatPDF::class.java)
    val provider = PdfViewerProvider()

    Assertions.assertFalse(provider.canSupport(preferences, book, format))
  }

  @Test
  fun supportsPdfBooksWhenEnabled() {
    val preferences = ViewerPreferences(
      // by setting the old PDF reader setting to false, we're enabling the PDF-JS reader
      flags = mapOf(
        "enableOldPDFReader" to false
      )
    )

    val book = Mockito.mock(Book::class.java)
    val format = Mockito.mock(BookFormat.BookFormatPDF::class.java)
    val provider = PdfViewerProvider()

    Assertions.assertTrue(provider.canSupport(preferences, book, format))
  }

  @Test
  fun potentiallySupportsPdfFiles() {
    val provider = PdfViewerProvider()

    Assertions.assertTrue(
      provider.canPotentiallySupportType(
        MIMEParser.parseRaisingException("application/pdf")
      )
    )
  }
}
