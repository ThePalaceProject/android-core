package org.nypl.simplified.tests.opds2

import org.nypl.simplified.opds2.parser.api.OPDS2ParsersType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds2.pwp.OPDS2ParsersPWP

class OPDS2ParserPWPTest : OPDS2ParserContract() {

  override val logger: Logger =
    LoggerFactory.getLogger(OPDS2ParserPWPTest::class.java)

  override fun createParsers(): OPDS2ParsersType {
    return OPDS2ParsersPWP
  }
}
