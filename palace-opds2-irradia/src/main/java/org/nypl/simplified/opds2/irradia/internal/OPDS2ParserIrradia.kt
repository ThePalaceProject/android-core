package org.nypl.simplified.opds2.irradia.internal

import one.irradia.opds2_0.api.OPDS20ParseError
import one.irradia.opds2_0.api.OPDS20ParseResult
import one.irradia.opds2_0.api.OPDS20ParseWarning
import one.irradia.opds2_0.parser.api.OPDS20FeedParserType
import org.nypl.simplified.opds2.OPDS2Feed
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.parser.api.ParseWarning
import org.nypl.simplified.parser.api.ParserType
import org.slf4j.LoggerFactory
import java.net.URI

internal class OPDS2ParserIrradia(
  private val uri: URI,
  private val parser: OPDS20FeedParserType,
  private val warningsAsErrors: Boolean
) : ParserType<OPDS2Feed> {

  private val logger =
    LoggerFactory.getLogger(OPDS2ParserIrradia::class.java)

  private fun OPDS20ParseWarning.toParseWarning(): ParseWarning {
    return ParseWarning(
      source = this@OPDS2ParserIrradia.uri,
      message = this.message,
      line = this.position.line,
      column = this.position.column,
      exception = this.exception
    )
  }

  private fun OPDS20ParseError.toParseError(): ParseError {
    return ParseError(
      source = this@OPDS2ParserIrradia.uri,
      message = this.message,
      line = this.position.line,
      column = this.position.column,
      exception = this.exception
    )
  }

  private fun <T> OPDS20ParseResult<T>.toParseResult(): ParseResult<T> {
    return when (this) {
      is OPDS20ParseResult.OPDS20ParseSucceeded ->
        ParseResult.Success(
          this.warnings.map { it.toParseWarning() },
          this.result
        )
      is OPDS20ParseResult.OPDS20ParseFailed ->
        ParseResult.Failure(
          this.warnings.map { it.toParseWarning() },
          this.errors.map { it.toParseError() }
        )
    }
  }

  override fun parse(): ParseResult<OPDS2Feed> {
    val timeThen = System.nanoTime()
    try {
      return this.parser.parse()
        .toParseResult()
        .flatMap { OPDS2IrradiaFeedConverter(it).convert() }
    } finally {
      val timeNow = System.nanoTime()
      val timeDiff = (timeNow - timeThen).toDouble()
      val timeDiffMS = timeDiff / 1_000_000.0
      this.logger.debug("Parsed feed in {} ms.", timeDiffMS)
    }
  }

  override fun close() {
    this.parser.close()
  }
}
