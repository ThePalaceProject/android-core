package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID


/**
 * A corrupt feed entry that cannot be read from the database.
 */
data class FeedEntryCorrupt(
  override val accountID: AccountID,
  override val bookID: FeedBookID,
  val error: Throwable
) : FeedEntry
