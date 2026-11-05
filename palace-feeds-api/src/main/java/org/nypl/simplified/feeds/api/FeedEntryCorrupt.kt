package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID

/**
 * A corrupt feed entry that cannot be read from the database.
 */
data class FeedEntryCorrupt(
  override val accountID: AccountID,
  override val bookID: BookID,
  val error: Throwable
) : FeedEntry, java.io.Serializable
