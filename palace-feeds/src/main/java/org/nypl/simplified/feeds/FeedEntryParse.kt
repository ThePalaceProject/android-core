package org.nypl.simplified.feeds

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryParseType
import org.nypl.simplified.feeds.opds12.OPDS12FeedConverter
import org.nypl.simplified.feeds.opds20.OPDS20FeedConverter
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParserType
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

/**
 * A [FeedEntryParseType] that handles both OPDS 1.2 and OPDS 2.0
 * serializations. The [contentType] is used to dispatch to the
 * appropriate parser. Callers are not required to know the format
 * of the input.
 */
class FeedEntryParse(
  private val opds12Parser: OPDSAcquisitionFeedEntryParserType,
) : FeedEntryParseType {
  private val log = LoggerFactory.getLogger(FeedEntryParse::class.java)

  override fun parseEntry(
    accountID: AccountID,
    stream: InputStream,
    uri: URI,
    contentType: MIMEType?,
  ): FeedEntryBook {
    if (contentType != null && contentType.fullType == CONTENT_TYPE_OPDS_JSON) {
      return this.parseOPDS20(accountID, stream, uri)
    }
    return this.parseOPDS12(accountID, stream, uri)
  }

  private fun parseOPDS20(
    accountID: AccountID,
    stream: InputStream,
    uri: URI,
  ): FeedEntryBook {
    //
    // A single-entry borrow response in OPDS 2.0 is either a
    // WPMPublication directly, or a WPMFeed with a single publication.
    // We buffer the stream because we may need to try both.
    //
    val bytes = stream.readBytes()

    try {
      val publication = OPDS20FeedConverter.parsePublication(ByteArrayInputStream(bytes))
      return OPDS20FeedConverter.convertEntry(accountID, publication)
    } catch (e: Exception) {
      this.log.debug("Failed to parse as WPMPublication, trying WPMFeed: ", e)
    }

    val feed = OPDS20FeedConverter.parse(ByteArrayInputStream(bytes))
    val publication = feed.publications.firstOrNull()
    if (publication == null) {
      throw Exception("OPDS 2.0 feed contains no publications: $uri")
    }

    return OPDS20FeedConverter.convertEntry(accountID, publication)
  }

  private fun parseOPDS12(
    accountID: AccountID,
    stream: InputStream,
    uri: URI,
  ): FeedEntryBook {
    val opdsEntry = stream.use { s -> this.opds12Parser.parseEntryStream(uri, s) }
    return OPDS12FeedConverter.convertEntry(accountID, opdsEntry)
  }

  companion object {
    private const val CONTENT_TYPE_OPDS_JSON = "application/opds+json"

    /**
     * Create a new [FeedEntryParse] instance.
     */
    fun create(): FeedEntryParseType =
      FeedEntryParse(
        opds12Parser = OPDSAcquisitionFeedEntryParser.newParser(),
      )
  }
}
