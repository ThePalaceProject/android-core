package org.nypl.simplified.ui.catalog

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.feeds.api.FeedEntryBook

data class CatalogBorrowParameters(
  val accountID: AccountID,
  val bookID: BookID,
  val entry: FeedEntryBook,
  val samlDownloadContext: SAMLDownloadContext?,
)
