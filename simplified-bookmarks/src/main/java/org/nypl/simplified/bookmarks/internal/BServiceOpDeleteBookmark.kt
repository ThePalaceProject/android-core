package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks.
 */

internal class BServiceOpDeleteBookmark(
  logger: Logger,
  private val httpCalls: BookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: Bookmark
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    this.remotelyDeleteBookmark()
    this.locallyDeleteBookmark()
  }

  private fun remotelyDeleteBookmark() {
    try {
      this.logger.debug(
        "[{}]: remote deleting bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val bookmarkURI = this.bookmark.uri
      if (bookmarkURI == null) {
        this.logger.debug(
          "[{}]: cannot remotely delete bookmark {} because it has no URI",
          this.profile.id.uuid,
          this.bookmark.bookmarkId.value
        )
        return
      }

      val syncInfo = BSyncableAccount.ofAccount(this.profile.account(this.accountID))
      if (syncInfo == null) {
        this.logger.debug(
          "[{}]: cannot remotely delete bookmark {} because the account is not syncable",
          this.profile.id.uuid,
          this.bookmark.bookmarkId.value
        )
        return
      }

      this.httpCalls.bookmarkDelete(
        bookmarkURI = bookmarkURI,
        credentials = syncInfo.credentials
      )
    } catch (e: Exception) {
      this.logger.error("[{}]: error deleting bookmark: ", this.profile.id.uuid, e)
    }
  }

  private fun locallyDeleteBookmark() {
    try {
      this.logger.debug(
        "[{}]: locally deleting bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val account = this.profile.account(this.accountID)
      val books = account.bookDatabase
      val entry = books.entry(this.bookmark.book)

      when (bookmark) {
        is Bookmark.ReaderBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB::class.java)

          if (handle != null) {
            when (this.bookmark.kind) {
              BookmarkKind.BookmarkLastReadLocation ->
                handle.setLastReadLocation(null)
              BookmarkKind.BookmarkExplicit ->
                handle.setBookmarks(handle.format.bookmarks.minus(this.bookmark))
            }
          } else {
            this.logger.debug(
              "[{}]: unable to delete bookmark; no format handle",
              this.profile.id.uuid
            )
          }
        }
        is Bookmark.AudiobookBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook::class.java)

          if (handle != null) {
            when (this.bookmark.kind) {
              BookmarkKind.BookmarkLastReadLocation ->
                handle.setLastReadLocation(null)
              BookmarkKind.BookmarkExplicit ->
                handle.setBookmarks(handle.format.bookmarks.minus(this.bookmark))
            }
          } else {
            this.logger.debug(
              "[{}]: unable to delete bookmark; no format handle",
              this.profile.id.uuid
            )
          }
        }
        else ->
          throw IllegalStateException("Unsupported bookmark type: $bookmark")
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error deleting bookmark locally: ", this.profile.id.uuid, e)
    }
  }
}
