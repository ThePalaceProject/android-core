package org.nypl.simplified.ui.catalog

import androidx.annotation.UiThread
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.taskrecorder.api.TaskResult
import java.net.URI

/**
 * The various operations that can be invoked by views in the catalog.
 */

interface CatalogViewCallbacksType {

  /**
   * Something wants to borrow a book.
   */

  @UiThread
  fun onBookRequestBorrow(
    parameters: CatalogBorrowParameters
  )

  /**
   * Something wants to revoke a book.
   */

  @UiThread
  fun onBookRequestRevoke(
    book: Book
  )

  /**
   * Determine if a book can be revoked.
   *
   * @return `true` if the book can be revoked.
   */

  @UiThread
  fun onBookCanBeRevoked(
    book: Book,
    status: BookStatus
  ): Boolean

  /**
   * A book was selected.
   */

  @UiThread
  fun onBookSelected(
    entry: FeedEntry.FeedEntryOPDS
  )

  /**
   * A request was made to open the preview of a book.
   */

  @UiThread
  fun onBookRequestPreviewOpen(
    book: Book
  )

  /**
   * A request was made to dismiss the error status of a book.
   */

  @UiThread
  fun onBookRequestDismissError(
    book: Book
  )

  /**
   * A request was made to delete a book.
   */

  @UiThread
  fun onBookRequestDelete(
    book: Book
  )

  /**
   * A request was made to cancel the borrowing of a book.
   */

  @UiThread
  fun onBookRequestBorrowCancel(
    book: Book
  )

  /**
   * A request was made to open a book for viewing.
   */

  @UiThread
  fun onBookRequestViewerOpen(
    book: Book,
    bookFormat: BookFormat
  )

  /**
   * A request was made to perform a SAML download of a book.
   */

  @UiThread
  @Deprecated("Unclear why this has its own special method.")
  fun onBookRequestSAMLDownload(
    status: CatalogBookStatus<BookStatus.DownloadWaitingForExternalAuthentication>
  )

  /**
   * A request was made to display error details for a failed task.
   */

  @UiThread
  fun onErrorDetailsDisplayRequested(
    error: TaskResult.Failure<*>
  )

  /**
   * The toolbar back button was pressed.
   */

  @UiThread
  fun onToolbarBackPressed()

  /**
   * The toolbar logo was clicked.
   */

  @UiThread
  fun onToolbarLogoPressed(currentAccount: AccountID)

  /**
   * A request was made to open a feed.
   */

  @UiThread
  fun onFeedSelected(
    accountID: AccountID,
    title: String,
    uri: URI
  )

  /**
   * A request was made to open a feed facet.
   */

  @UiThread
  fun onFeedFacetSelected(
    feedFacet: FeedFacet
  )

  /**
   * A search request was submitted.
   */

  @UiThread
  fun onSearchSubmitted(
    accountID: AccountID,
    feedSearch: FeedSearch,
    queryText: String
  )

  /**
   * @return `true` if the given book appears to be returnable
   */

  @UiThread
  fun onIsBookReturnable(book: Book): Boolean

  /**
   * @return `true` if the given book appears to be deletable
   */

  @UiThread
  fun onIsBookDeletable(book: Book): Boolean
}
