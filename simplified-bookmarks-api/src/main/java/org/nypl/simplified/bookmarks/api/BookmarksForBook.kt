package org.nypl.simplified.bookmarks.api

import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import java.io.Serializable

/**
 * A set of bookmarks for a specific book.
 *
 * <p>Note: The type is {@link Serializable} purely because the Android API requires this
 * in order pass values of this type between activities. We make absolutely no guarantees
 * that serialized values of this class will be compatible with future releases.</p>
 */

data class BookmarksForBook(
  val bookId: BookID,
  val lastRead: SerializedBookmark?,
  val bookmarks: List<SerializedBookmark>
) : Serializable {
  init {
    check(this.bookmarks.all { bookmark -> bookmark.kind == BookmarkKind.BookmarkExplicit }) {
      "All bookmarks must be explicit bookmarks."
    }
    check(this.bookmarks.all { bookmark -> bookmark.book == this.bookId }) {
      "All bookmarks must be for book ${this.bookId}."
    }
    if (this.lastRead != null) {
      check(this.lastRead.kind == BookmarkKind.BookmarkLastReadLocation) {
        "Last-read bookmark must be of a last-read kind"
      }
      check(this.lastRead.book == this.bookId) {
        "All bookmarks must be for book ${this.bookId}."
      }
    }
  }

  companion object {
    fun empty(bookID: BookID): BookmarksForBook {
      return BookmarksForBook(
        bookId = bookID,
        lastRead = null,
        bookmarks = listOf()
      )
    }
  }
}
