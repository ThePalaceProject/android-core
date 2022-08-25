package org.nypl.simplified.viewer.pdf

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceUsableType
import org.nypl.simplified.bookmarks.api.Bookmarks
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

internal object PdfReaderBookmarks {

  private val logger =
    LoggerFactory.getLogger(PdfReaderBookmarks::class.java)

  private fun loadRawBookmarks(
    bookmarkService: BookmarkServiceUsableType,
    accountID: AccountID,
    bookID: BookID
  ): Bookmarks {
    return try {
      bookmarkService
        .bookmarkSyncAndLoad(accountID, bookID)
        .get(15L, TimeUnit.SECONDS)
    } catch (e: Exception) {
      this.logger.error("could not load bookmarks: ", e)
      Bookmarks(null, emptyList())
    }
  }

  /**
   * Load bookmarks from the given bookmark service.
   */

  fun loadBookmarks(
    bookmarkService: BookmarkServiceUsableType,
    accountID: AccountID,
    bookID: BookID
  ): List<Bookmark> {
    val rawBookmarks =
      this.loadRawBookmarks(
        bookmarkService = bookmarkService,
        accountID = accountID,
        bookID = bookID
      )
    val lastRead = rawBookmarks.lastRead
    val explicits = rawBookmarks.bookmarks

    val results = mutableListOf<Bookmark>()
    lastRead?.let(results::add)
    results.addAll(explicits)
    return results.toList()
  }
}
