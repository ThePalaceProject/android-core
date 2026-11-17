package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.accounts.api.AccountID
import java.io.InputStream
import java.net.URI

/**
 * A type that can parse a single feed entry from a stream, regardless of
 * the serialization format (OPDS 1.2 or OPDS 2.0).
 *
 * Implementations use the [contentType] to dispatch to the appropriate
 * parser internally. Callers are not required to know the format of the
 * input.
 */
interface FeedEntryParseType {
  /**
   * Parse a single feed entry from the given [stream].
   *
   * @param accountID The account that the entry belongs to
   * @param stream The input stream containing the entry serialization
   * @param uri The URI that the entry was fetched from
   * @param contentType The MIME type of the response body, if known
   * @return The parsed [FeedEntryBook]
   */
  @Throws(Exception::class)
  fun parseEntry(
    accountID: AccountID,
    stream: InputStream,
    uri: URI,
    contentType: MIMEType?,
  ): FeedEntryBook
}
