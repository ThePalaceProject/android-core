package org.nypl.simplified.ui.main

import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.simplified.books.audio.AudioBookFeedbooksSecretServiceType
import org.nypl.simplified.books.audio.AudioBookOverdriveSecretServiceType
import org.nypl.simplified.books.formats.BookFormatAudioSupportParameters
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.readium.r2.lcp.LcpService
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

object MainBookFormatSupport {

  private val logger =
    LoggerFactory.getLogger(MainBookFormatSupport::class.java)

  fun createBookFormatSupport(
    adobeDRM: AdobeAdeptExecutorType?,
    lcpService: LcpService?,
    boundless: BoundlessServiceType?,
    feedbooksSecretService: AudioBookFeedbooksSecretServiceType?,
    overdriveSecretService: AudioBookOverdriveSecretServiceType?
  ): BookFormatSupportType {
    val parameters =
      BookFormatSupportParameters(
        supportsPDF = isPDFSupported(),
        supportsAdobeDRM = adobeDRM != null,
        supportsBoundless = boundless != null,
        supportsAudioBooks = BookFormatAudioSupportParameters(
          supportsFindawayAudioBooks = isFindawaySupported(),
          supportsOverdriveAudioBooks = overdriveSecretService != null,
          supportsDPLAAudioBooks = feedbooksSecretService != null
        ),
        supportsLCP = lcpService != null
      )

    return BookFormatSupport.create(parameters)
  }

  /**
   * XXX: This is not correct, but we don't currently have a means to detect the truth.
   */

  private fun isFindawaySupported(): Boolean {
    return true
  }

  private fun isPDFSupported(): Boolean {
    try {
      val viewers =
        ServiceLoader.load(ViewerProviderType::class.java)
          .toList()

      for (viewer in viewers) {
        if (viewer.canPotentiallySupportType(StandardFormatNames.genericPDFFiles)) {
          return true
        }
      }
      return false
    } catch (e: Exception) {
      logger.debug("one or more viewer providers raised an exception: ", e)
      return false
    }
  }
}
