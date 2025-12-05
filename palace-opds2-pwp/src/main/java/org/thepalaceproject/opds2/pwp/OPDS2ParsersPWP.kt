package org.thepalaceproject.opds2.pwp

import org.nypl.simplified.opds2.OPDS2Feed
import org.nypl.simplified.opds2.parser.api.OPDS2ParsersType
import org.nypl.simplified.parser.api.ParserType
import org.thepalaceproject.opds2.pwp.internal.OPDS2ParserPWP
import org.thepalaceproject.webpub.core.WPMMappers
import java.io.InputStream
import java.net.URI

object OPDS2ParsersPWP : OPDS2ParsersType {

  private val mapper =
    WPMMappers.createMapper()

  override fun createParser(
    uri: URI,
    stream: InputStream,
    warningsAsErrors: Boolean
  ): ParserType<OPDS2Feed> {
    return OPDS2ParserPWP(
      source = uri,
      stream = stream,
      mapper = this.mapper
    )
  }
}
