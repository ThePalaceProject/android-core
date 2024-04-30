package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that loads bookmarks.
 */

internal class BServiceOpLoadBookmarks(
  logger: Logger,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val book: BookID
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

        return BookmarksForBook(
          bookId = this.book,
          lastRead = lastReadLocation,
          bookmarks = bookmarks
        )
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error loading bookmarks: ", this.profile.id.uuid, e)
    }

    this.logger.debug("[{}]: returning empty bookmarks", this.profile.id.uuid)
    return BookmarksForBook(this.book, null, listOf())
  }
}
