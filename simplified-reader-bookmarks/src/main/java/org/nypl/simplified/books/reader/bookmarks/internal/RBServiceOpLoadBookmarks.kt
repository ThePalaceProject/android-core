package org.nypl.simplified.books.reader.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarks
import org.slf4j.Logger

/**
 * An operation that loads bookmarks.
 */

internal class RBServiceOpLoadBookmarks(
  logger: Logger,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val book: BookID
) : RBServiceOp<ReaderBookmarks>(logger) {

  override fun runActual(): ReaderBookmarks {
    try {
      this.logger.debug("[{}]: loading bookmarks", this.profile.id.uuid)

      val account = this.profile.account(this.accountID)
      val books = account.bookDatabase
      val entry = books.entry(this.book)
      val handle = entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
      if (handle != null) {
        this.logger.debug(
          "[{}]: loaded {} bookmarks",
          this.profile.id.uuid,
          handle.format.bookmarks.size
        )

        return ReaderBookmarks(
          handle.format.lastReadLocation,
          handle.format.bookmarks
        )
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error loading bookmarks: ", this.profile.id.uuid, e)
    }

    this.logger.debug("[{}]: returning empty bookmarks", this.profile.id.uuid)
    return ReaderBookmarks(null, listOf())
  }
}
