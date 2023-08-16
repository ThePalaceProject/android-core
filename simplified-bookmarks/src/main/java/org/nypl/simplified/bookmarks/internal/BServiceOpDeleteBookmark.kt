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
  private val bookmark: Bookmark,
  private val ignoreRemoteFailures: Boolean
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    try {
      this.remotelyDeleteBookmark()
    } catch (e: Exception) {
      this.logger.debug(
        "[{}]: failed to delete bookmark {}: ",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value,
        e
      )
      if (!this.ignoreRemoteFailures) {
        throw e
      }
    }

    this.locallyDeleteBookmark()
  }

  private fun remotelyDeleteBookmark() {
    return try {
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
        throw IllegalStateException("Bookmark has no URI.")
      }

      val syncInfo = BSyncableAccount.ofAccount(this.profile.account(this.accountID))
      if (syncInfo == null) {
        this.logger.debug(
          "[{}]: cannot remotely delete bookmark {} because the account is not syncable",
          this.profile.id.uuid,
          this.bookmark.bookmarkId.value
        )
        throw IllegalStateException("Account is not syncable.")
      }

      this.httpCalls.bookmarkDelete(
        bookmarkURI = bookmarkURI,
        credentials = syncInfo.credentials
      )
      Unit
    } catch (e: Exception) {
      this.logger.error("[{}]: error deleting bookmark: ", this.profile.id.uuid, e)
      throw e
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

      when (this.bookmark) {
        is Bookmark.ReaderBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB::class.java)
              ?: throw this.errorNoFormatHandle()

          when (this.bookmark.kind) {
            BookmarkKind.BookmarkLastReadLocation ->
              handle.setLastReadLocation(null)
            BookmarkKind.BookmarkExplicit ->
              handle.setBookmarks(handle.format.bookmarks.minus(this.bookmark))
          }
        }
        is Bookmark.PDFBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF::class.java)
              ?: throw this.errorNoFormatHandle()

          when (this.bookmark.kind) {
            BookmarkKind.BookmarkLastReadLocation ->
              handle.setLastReadLocation(null)
            BookmarkKind.BookmarkExplicit -> {
              handle.setBookmarks(handle.format.bookmarks.minus(this.bookmark))
            }
          }
        }
        is Bookmark.AudiobookBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook::class.java)
              ?: throw this.errorNoFormatHandle()

          when (this.bookmark.kind) {
            BookmarkKind.BookmarkLastReadLocation ->
              handle.setLastReadLocation(null)
            BookmarkKind.BookmarkExplicit ->
              handle.setBookmarks(handle.format.bookmarks.minus(this.bookmark))
          }
        }
        else ->
          throw IllegalStateException("Unsupported bookmark type: ${this.bookmark}")
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error deleting bookmark locally: ", this.profile.id.uuid, e)
      throw e
    }
  }

  private fun errorNoFormatHandle(): IllegalStateException {
    this.logger.debug(
      "[{}]: unable to delete bookmark; no format handle",
      this.profile.id.uuid
    )
    return IllegalStateException("No format handle")
  }
}
