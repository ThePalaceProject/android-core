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

    var forAccount: Map<BookID, BookmarksForBook> =
      data[account] ?: mapOf()
    var forBook: BookmarksForBook =
      forAccount[bookmark.book] ?: BookmarksForBook(
        bookId = bookmark.book,
        lastRead = isLastRead,
        bookmarks = listOf()
      )

    if (isLastRead != null) {
      forBook = forBook.copy(lastRead = isLastRead)
    } else {
      /*
       * Thanks to the awful design of the existing bookmark formats, there's no better way
       * to deduplicate bookmarks than linearly searching through the entire list. If bookmarks
       * carried unique client-assigned identifiers, then the bookmarks could be stored in a map
       * and this would be efficient.
       */

      val newList = forBook.bookmarks.toMutableList()
      newList.removeIf { b -> b.isInterchangeableWith(bookmark) }
      newList.add(bookmark)
      forBook = forBook.copy(bookmarks = newList.toList())
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
        val forAccount: Map<BookID, BookmarksForBook> =
          data[account] ?: mapOf()
        var forBook: BookmarksForBook =
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
    var forAccount: Map<BookID, BookmarksForBook> = data[account] ?: mapOf()
    forAccount = forAccount.plus(Pair(bookmarksForBook.bookId, bookmarksForBook))
    return data.plus(Pair(account, forAccount))
  }
}
