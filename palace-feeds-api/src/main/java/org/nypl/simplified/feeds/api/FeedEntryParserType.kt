package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import java.io.IOException

/**
 * The type of parsers that consume JSON and produce a [FeedEntryBook].
 */
interface FeedEntryParserType {
  /**
   * Parse a feed entry from the given JSON string.
   *
   * @param accountID The account ID to associate with the entry.
   * @param json The JSON string.
   * @return The feed entry.
   * @throws IOException On parse errors.
   */
  @Throws(IOException::class)
  fun parseEntry(
    accountID: AccountID,
    json: String,
  ): FeedEntryBook
}
