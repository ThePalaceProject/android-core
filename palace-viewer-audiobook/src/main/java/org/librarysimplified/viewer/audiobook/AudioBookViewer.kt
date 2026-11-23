package org.librarysimplified.viewer.audiobook

import android.app.Activity
import one.irradia.mime.api.MIMEType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.viewer.spi.ViewerParameters
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * An audio book viewer service.
 *
 * The entry point into the open pipeline. It delegates to
 * [AudiobookOpener], which is activity-free; the [Activity] here is used only to
 * launch the player screen.
 */

class AudioBookViewer : ViewerProviderType {
  private val logger =
    LoggerFactory.getLogger(AudioBookViewer::class.java)

  override val name: String =
    "org.librarysimplified.viewer.audiobook.AudioBookViewer"

  override fun canSupport(
    preferences: ViewerParameters,
    book: Book,
    format: BookFormat,
  ): Boolean =
    when (format) {
      is BookFormat.BookFormatEPUB,
      is BookFormat.BookFormatPDF,
      -> {
        this.logger.debug("audio book viewer can only view audio books")
        false
      }

      is BookFormat.BookFormatAudioBook -> {
        true
      }
    }

  override fun canPotentiallySupportType(type: MIMEType): Boolean = StandardFormatNames.allAudioBooks.contains(type)

  override fun open(
    activity: Activity,
    parameters: ViewerParameters,
    book: Book,
    format: BookFormat,
    accountProviderId: URI,
  ) {
    AudiobookOpener.open(
      context = activity,
      parameters = parameters,
      book = book,
      format = format,
      launchUI = true,
    )
  }
}
