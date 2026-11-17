package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryParserType
import org.nypl.simplified.opds.core.OPDSJSONParserType
import java.io.IOException
import java.io.InputStream

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
    stream: InputStream,
  ): FeedEntryBook {
    val opdsEntry = this.opdsParser.parseAcquisitionFeedEntryFromStream(stream)
    return OPDS12FeedConverter.convertEntry(accountID, opdsEntry)
  }
}
