package org.nypl.simplified.books.borrowing

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntryBook

/**
 * The type of requests to borrow books.
 */

sealed class BorrowRequest {
  /**
   * The OPDS feed entry that will be borrowed.
   */

  abstract val opdsAcquisitionFeedEntry: FeedEntryBook

  /**
   * The ID of the account.
   */

  abstract val accountId: AccountID

  /**
   * Information about the current SAML download, if one is in progress.
   */

  abstract val samlDownloadContext: SAMLDownloadContext?

  /**
   * Start borrowing a book.
   */

  data class Start(
    override val accountId: AccountID,
    override val opdsAcquisitionFeedEntry: FeedEntryBook,
    override val samlDownloadContext: SAMLDownloadContext? = null,
  
  ) : BorrowRequest()
}
