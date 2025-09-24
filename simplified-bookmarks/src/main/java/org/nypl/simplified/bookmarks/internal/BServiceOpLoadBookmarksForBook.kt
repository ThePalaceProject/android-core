package org.nypl.simplified.bookmarks.internal

import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that loads bookmarks.
 */

internal class BServiceOpLoadBookmarksForBook(
  logger: Logger,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val book: BookID,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<BookmarksForBook>(logger) {

  override fun runActual(): BookmarksForBook {
    try {
      this.logger.debug("[{}]: loading bookmarks for book {}", this.profile.id.uuid, this.book.brief())

      val account = this.profile.account(this.accountID)
      val books = account.bookDatabase
      val entry = books.entry(this.book)
      val handle = entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
        ?: entry.findFormatHandle(BookDatabaseEntryFormatHandleAudioBook::class.java)
        ?: entry.findFormatHandle(BookDatabaseEntryFormatHandlePDF::class.java)

      if (handle != null) {
        val bookmarks: List<SerializedBookmark>
        val lastReadLocation: SerializedBookmark?

        when (handle.format) {
          is BookFormat.BookFormatEPUB -> {
            val format = handle.format as BookFormat.BookFormatEPUB
            bookmarks = format.bookmarks
            lastReadLocation = format.lastReadLocation
          }
          is BookFormat.BookFormatAudioBook -> {
            val format = handle.format as BookFormat.BookFormatAudioBook
            bookmarks = format.bookmarks
            lastReadLocation = format.lastReadLocation
          }
          is BookFormat.BookFormatPDF -> {
            val format = handle.format as BookFormat.BookFormatPDF
            bookmarks = format.bookmarks
            lastReadLocation = format.lastReadLocation
          }
          else -> {
            bookmarks = emptyList()
            lastReadLocation = null
          }
        }

        this.logger.debug(
          "[{}]: loaded {} bookmarks",
          this.profile.id.uuid,
          bookmarks.size
        )

        return publish(BookmarksForBook(
          bookId = this.book,
          lastRead = lastReadLocation,
          bookmarks = bookmarks.filter { b -> b.kind == BookmarkKind.BookmarkExplicit }
        ))
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error loading bookmarks: ", this.profile.id.uuid, e)
    }

    this.logger.debug("[{}]: returning empty bookmarks", this.profile.id.uuid)
    return publish(BookmarksForBook(this.book, null, listOf()))
  }

  private fun publish(
    bookmarksForBook: BookmarksForBook
  ): BookmarksForBook {
    this.bookmarksSource.set(
      BookmarkAttributes.addBookmarks(
        this.bookmarksSource.get(),
        this.accountID,
        bookmarksForBook
      )
    )
    return bookmarksForBook
  }
}
