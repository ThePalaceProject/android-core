package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark

/**
 * Functions over bookmark attributes.
 */

object BookmarkAttributes {

  fun removeAccount(
    data: Map<AccountID, Map<BookID, BookmarksForBook>>,
    account: AccountID
  ): Map<AccountID, Map<BookID, BookmarksForBook>> {
    return data.minus(account)
  }

  fun addBookmark(
    data: Map<AccountID, Map<BookID, BookmarksForBook>>,
    account: AccountID,
    bookmark: SerializedBookmark
  ): Map<AccountID, Map<BookID, BookmarksForBook>> {
    val isLastRead =
      if (bookmark.kind == BookmarkKind.BookmarkLastReadLocation) {
        bookmark
      } else {
        null
      }

    var forAccount =
      data[account] ?: mapOf()
    var forBook =
      forAccount[bookmark.book] ?: BookmarksForBook(
        bookId = bookmark.book,
        lastRead = isLastRead,
        bookmarks = listOf()
      )

    if (isLastRead != null) {
      forBook = forBook.copy(lastRead = isLastRead)
    } else {
      forBook = forBook.copy(bookmarks = forBook.bookmarks.plus(bookmark))
    }

    forAccount = forAccount.plus(Pair(bookmark.book, forBook))
    return data.plus(Pair(account, forAccount))
  }

  fun removeBookmark(
    data: Map<AccountID, Map<BookID, BookmarksForBook>>,
    account: AccountID,
    bookmark: SerializedBookmark
  ): Map<AccountID, Map<BookID, BookmarksForBook>> {
    return when (bookmark.kind) {
      BookmarkKind.BookmarkExplicit -> {
        val forAccount =
          data[account] ?: mapOf()
        var forBook =
          forAccount[bookmark.book] ?: BookmarksForBook(
            bookId = bookmark.book,
            lastRead = null,
            bookmarks = listOf()
          )

        forBook = forBook.copy(bookmarks = forBook.bookmarks.minus(bookmark))
        this.addBookmarks(data, account, forBook)
      }

      BookmarkKind.BookmarkLastReadLocation -> data
    }
  }

  fun addBookmarks(
    data: Map<AccountID, Map<BookID, BookmarksForBook>>,
    account: AccountID,
    bookmarksForBook: BookmarksForBook
  ): Map<AccountID, Map<BookID, BookmarksForBook>> {
    var forAccount = data[account] ?: mapOf()
    forAccount = forAccount.plus(Pair(bookmarksForBook.bookId, bookmarksForBook))
    return data.plus(Pair(account, forAccount))
  }
}
