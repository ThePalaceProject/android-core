package org.librarysimplified.viewer.pdf.androidx

import android.app.Activity
import android.content.Intent
import one.irradia.mime.api.MIMEType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory
import java.net.URI

class AXPDFViewer : ViewerProviderType {

  private val logger =
    LoggerFactory.getLogger(AXPDFViewer::class.java)

  override val name =
    "org.librarysimplified.viewer.pdf.androidx.AXPDFViewer"

  override fun canSupport(
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat
  ): Boolean {
    val androidXEnabled = preferences.flags["UseAndroidXPDF"] ?: false
    return if (androidXEnabled) {
      when (format) {
        is BookFormat.BookFormatEPUB,
        is BookFormat.BookFormatAudioBook -> {
          this.logger.debug("The AndroidX PDF viewer can only open PDF files")
          false
        }
        is BookFormat.BookFormatPDF -> {
          true
        }
      }
    } else {
      this.logger.debug("The AndroidX PDF viewer is not enabled")
      false
    }
  }

  override fun canPotentiallySupportType(type: MIMEType): Boolean {
    return type == StandardFormatNames.genericPDFFiles
  }

  override fun open(
    activity: Activity,
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat,
    accountProviderID: URI
  ) {
    activity.startActivity(Intent(activity, AXPDFActivity::class.java))
  }
}
