package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import java.io.IOException
import java.io.InputStream

/**
 * The type of parsers that consume JSON and produce a [FeedEntryBook].
 */
interface FeedEntryParserType {
  /**
   * Parse a feed entry from the given JSON stream.
   *
   * @param accountID The account ID to associate with the entry.
   * @param stream The JSON stream.
   * @return The feed entry.
   * @throws IOException On parse errors.
   */
  @Throws(IOException::class)
  fun parseEntry(
    accountID: AccountID,
    stream: InputStream,
  ): FeedEntryBook
}
