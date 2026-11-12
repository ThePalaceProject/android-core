package org.nypl.simplified.ui.catalog

import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.FeedBookIDConversions
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook

data class CatalogBookStatus<S : BookStatus>(
  val book: Book,
  val status: S,
  val previewStatus: BookPreviewStatus,
) {
  fun toBorrowParameters(): CatalogBorrowParameters =
    CatalogBorrowParameters(
      accountID = this.book.account,
      bookID = this.book.id,
      entry = this.book.entry,
      samlDownloadContext = null,
    )

  companion object {
    /**
     * Do everything necessary to get an up-to-date view of a book. Either the book is derived
     * entirely from the given OPDS feed entry, or a more recent status is taken from the book
     * registry.
     */

    fun create(
      registry: BookRegistryReadableType,
      feedEntry: FeedEntryBook,
    ): CatalogBookStatus<BookStatus> {
      val bookID = FeedBookIDConversions.toBookID(feedEntry.bookID)
      val registryStatus =
        registry.bookStatusOrNull(bookID)

      var book = registry.bookOrNull(bookID)?.book
      if (book == null) {
        book =
          Book(
            id = bookID,
            account = feedEntry.accountID,
            cover = null,
            thumbnail = null,
            entry = feedEntry,
            formats = listOf(),
          )
      }

      if (registryStatus != null) {
        return CatalogBookStatus(
          book,
          registryStatus,
          bookPreviewStatusOf(feedEntry),
        )
      } else {
        return CatalogBookStatus(
          book,
          BookStatus.fromBook(book),
          bookPreviewStatusOf(feedEntry),
        )
      }
    }

    private fun bookPreviewStatusOf(entry: FeedEntryBook): BookPreviewStatus {
      if (!entry.previewAcquisitions.isNullOrEmpty()) {
        return BookPreviewStatus.HasPreview()
      } else {
        return BookPreviewStatus.None
      }
    }
  }
}
