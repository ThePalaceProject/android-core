package org.nypl.simplified.tests.books

import org.nypl.simplified.books.formats.BookFormatAudioSupportParameters
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters

object BookFormatsTesting {

  val supportsEverything =
    BookFormatSupport.create(
      BookFormatSupportParameters(
        supportsEPUB = true,
        supportsLCP = true,
        supportsAudioBooks = BookFormatAudioSupportParameters(
          supportsDPLAAudioBooks = true,
          supportsFindawayAudioBooks = true,
          supportsOverdriveAudioBooks = true
        ),
        supportsBoundless = true,
        supportsPDF = true,
        supportsAdobeDRM = true
      )
    )

  val supportsOnlyEPUB =
    BookFormatSupport.create(
      BookFormatSupportParameters(
        supportsEPUB = true,
        supportsLCP = false,
        supportsAudioBooks = null,
        supportsBoundless = false,
        supportsPDF = false,
        supportsAdobeDRM = false
      )
    )

  val supportsNothing =
    BookFormatSupport.create(
      BookFormatSupportParameters(
        supportsEPUB = false,
        supportsLCP = false,
        supportsAudioBooks = null,
        supportsBoundless = false,
        supportsPDF = false,
        supportsAdobeDRM = false
      )
    )
}
