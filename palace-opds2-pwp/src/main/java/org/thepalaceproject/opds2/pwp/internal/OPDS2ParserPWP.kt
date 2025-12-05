package org.thepalaceproject.opds2.pwp.internal

import com.fasterxml.jackson.databind.json.JsonMapper
import org.nypl.simplified.opds2.OPDS2Feed
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.parser.api.ParserType
import org.slf4j.LoggerFactory
import org.thepalaceproject.webpub.core.WPMFeed
import java.io.InputStream
import java.net.URI

class OPDS2ParserPWP(
  private val source: URI,
  private val stream: InputStream,
  private val mapper: JsonMapper
) : ParserType<OPDS2Feed> {

  companion object {
    private val logger =
      LoggerFactory.getLogger(OPDS2ParserPWP::class.java)
  }

  override fun parse(): ParseResult<OPDS2Feed> {
    val data: ByteArray

    run {
      val timeThen = System.nanoTime()
      try {
        data = this.stream.readBytes()
      } finally {
        val timeNow = System.nanoTime()
        val timeDiff = (timeNow - timeThen).toDouble() / 1_000_000.0
        logger.debug("Consumed feed {} bytes in {} ms", this.source, timeDiff)
      }
    }

    run {
      val timeThen = System.nanoTime()
      try {
        return OPDS2FeedConverterPWP.convert(
          source = this.source,
          feed = this.mapper.readValue(data, WPMFeed::class.java)
        )
      } finally {
        val timeNow = System.nanoTime()
        val timeDiff = (timeNow - timeThen).toDouble() / 1_000_000.0
        logger.debug("Parsed feed {} in {} ms", this.source, timeDiff)
      }
    }
  }

  override fun close() {
    // Nothing required
  }
}
