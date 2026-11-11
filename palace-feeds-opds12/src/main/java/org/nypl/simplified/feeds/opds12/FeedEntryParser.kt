package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryParserType
import org.nypl.simplified.opds.core.OPDSJSONParserType
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * A [FeedEntryParserType] implementation that uses the OPDS 1.2 JSON parser.
 * Can read the same format produced by [FeedEntrySerializer].
 */
class FeedEntryParser(
  private val opdsParser: OPDSJSONParserType,
) : FeedEntryParserType {
  @Throws(IOException::class)
  override fun parseEntry(
    accountID: AccountID,
    json: String,
  ): FeedEntryBook {
    val stream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
    val opdsEntry = this.opdsParser.parseAcquisitionFeedEntryFromStream(stream)
    return OPDS12FeedConverter.convertEntry(accountID, opdsEntry)
  }
}
