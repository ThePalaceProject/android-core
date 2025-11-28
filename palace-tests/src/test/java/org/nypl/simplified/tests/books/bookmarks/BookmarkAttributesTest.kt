package org.nypl.simplified.tests.books.bookmarks

import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.bookmarks.internal.BookmarkAttributes
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmark20240424
import org.nypl.simplified.books.api.bookmark.SerializedLocatorHrefProgression20210317
import java.util.UUID

class BookmarkAttributesTest {

  private fun randomBookmark(
    opdsId: String
  ): SerializedBookmark {
    return SerializedBookmark20240424(
      0.5,
      "Chapter " + UUID.randomUUID(),
      0.5,
      "Book $opdsId",
      UUID.randomUUID().toString(),
      BookmarkKind.BookmarkExplicit,
      location = SerializedLocatorHrefProgression20210317(
        chapterHref = "page01.xhtml",
        chapterProgress = 0.5
      ),
      opdsId = opdsId,
      time = DateTime.now(),
      uri = null
    )
  }

  private fun lastReadBookmark(
    opdsId: String
  ): SerializedBookmark {
    return SerializedBookmark20240424(
      0.5,
      "Chapter " + UUID.randomUUID(),
      0.5,
      "Book $opdsId",
      UUID.randomUUID().toString(),
      BookmarkKind.BookmarkLastReadLocation,
      location = SerializedLocatorHrefProgression20210317(
        chapterHref = "page01.xhtml",
        chapterProgress = 0.5
      ),
      opdsId = opdsId,
      time = DateTime.now(),
      uri = null
    )
  }

  private fun randomBookmarks(
    bookCount: Int
  ): Map<BookID, BookmarksForBook> {
    val data = mutableMapOf<BookID, BookmarksForBook>()
    for (i in 0 until bookCount) {
      val opdsId =
        UUID.randomUUID().toString()
      val bookId =
        BookIDs.newFromText(opdsId)
      val bookmarks =
        BookmarksForBook(
          bookId,
          lastReadBookmark(opdsId),
          listOf(
            randomBookmark(opdsId),
            randomBookmark(opdsId),
            randomBookmark(opdsId),
          )
        )
      data[bookId] = bookmarks
    }
    return data.toMap()
  }

  @Test
  fun testRemoveAccountEmpty() {
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf()
    val y: Map<AccountID, Map<BookID, BookmarksForBook>> =
      BookmarkAttributes.removeAccount(x, AccountID.generate())

    assertEquals(x, y)
  }

  @Test
  fun testRemoveAccount() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val y = BookmarkAttributes.removeAccount(x, AccountID.generate())
    assertEquals(x, y)

    val xExpect: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp2)

    val xResult = BookmarkAttributes.removeAccount(x, acc1)
    assertEquals(xExpect, xResult)
  }

  @Test
  fun testAddBookmark() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val b = randomBookmark("abcd")
    val y = BookmarkAttributes.addBookmark(x, acc0, b)
    assertEquals(b, y[acc0]!![b.book]!!.bookmarks.last())
  }

  @Test
  fun testAddBookmarkFreshAccount() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()
    val acc3 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val b = randomBookmark("abcd")
    val y = BookmarkAttributes.addBookmark(x, acc3, b)
    assertEquals(b, y[acc3]!![b.book]!!.bookmarks.last())
  }

  @Test
  fun testAddBookmarkLastRead() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val b = lastReadBookmark("abcd")
    val y = BookmarkAttributes.addBookmark(x, acc0, b)
    assertEquals(b, y[acc0]!![b.book]!!.lastRead)
  }

  @Test
  fun testAddBookmarkLastReadFreshAccount() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()
    val acc3 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val b = lastReadBookmark("abcd")
    val y = BookmarkAttributes.addBookmark(x, acc3, b)
    assertEquals(b, y[acc3]!![b.book]!!.lastRead)
  }

  @Test
  fun testRemoveBookmark() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val b = xp0.second.values.last().bookmarks.last()
    val y = BookmarkAttributes.removeBookmark(x, acc0, b)
    assertNotEquals(b, y[acc0]!![b.book]!!.bookmarks.last())
  }

  @Test
  fun testRemoveBookmarkLastRead() {
    val acc0 = AccountID.generate()
    val acc1 = AccountID.generate()
    val acc2 = AccountID.generate()

    val xp0 = Pair(acc0, randomBookmarks(bookCount = 3))
    val xp1 = Pair(acc1, randomBookmarks(bookCount = 3))
    val xp2 = Pair(acc2, randomBookmarks(bookCount = 3))
    val x: Map<AccountID, Map<BookID, BookmarksForBook>> =
      mapOf(xp0, xp1, xp2)

    val b = lastReadBookmark("abcd")
    val y = BookmarkAttributes.removeBookmark(x, acc0, b)
    assertEquals(x, y)
  }
}
