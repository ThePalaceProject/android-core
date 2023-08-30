package org.librarysimplified.ui.catalog

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.errorpage.ErrorPageParameters

sealed class CatalogBookDetailEvent {

  object GoUpwards : CatalogBookDetailEvent()

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : CatalogBookDetailEvent()

  data class LoginRequired(
    val account: AccountID
  ) : CatalogBookDetailEvent()

  data class OpenViewer(
    val book: Book,
    val format: BookFormat
  ) : CatalogBookDetailEvent()

  data class OpenBookDetail(
    val feedArguments: CatalogFeedArguments,
    val opdsEntry: FeedEntry.FeedEntryOPDS
  ) : CatalogBookDetailEvent()

  data class OpenPreviewViewer(
    val feedEntry: FeedEntry.FeedEntryOPDS
  ) : CatalogBookDetailEvent()

  data class OpenFeed(
    val feedArguments: CatalogFeedArguments
  ) : CatalogBookDetailEvent()
}
