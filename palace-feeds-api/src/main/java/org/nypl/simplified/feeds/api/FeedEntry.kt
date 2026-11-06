package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID


/**
 * A feed entry.
 */
sealed interface FeedEntry {
  /**
   * The account that owns the feed entry.
   */
  val accountID: AccountID

  /**
   * The ID of the book within the feed entry.
   */
  val bookID: FeedBookID
}
