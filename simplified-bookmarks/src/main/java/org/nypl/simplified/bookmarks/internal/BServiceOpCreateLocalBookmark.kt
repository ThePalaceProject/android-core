package org.nypl.simplified.bookmarks.internal

import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks.
 */

internal class BServiceOpCreateLocalBookmark(
  logger: Logger,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: Bookmark
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    this.locallySaveBookmark()
  }

  private fun locallySaveBookmark() {
    try {
      this.logger.debug(
        "[{}]: locally saving bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val account = this.profile.account(this.accountID)
      val books = account.bookDatabase
      val entry = books.entry(this.bookmark.book)

      when (bookmark) {
        is Bookmark.ReaderBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)

          if (handle != null) {
            when (this.bookmark.kind) {
              BookmarkKind.BookmarkLastReadLocation ->
                handle.setLastReadLocation(this.bookmark)
              BookmarkKind.BookmarkExplicit ->
                handle.setBookmarks(
                  BServiceBookmarks.normalizeBookmarks(
                    logger = this.logger,
                    profileId = this.profile.id,
                    handle = handle,
                    bookmark = bookmark
                  )
                )
            }

            this.bookmarkEventsOut.onNext(
              BookmarkEvent.BookmarkSaved(
                this.accountID,
                this.bookmark
              )
            )
          } else {
            this.logger.debug(
              "[{}]: unable to save bookmark; no format handle",
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
                handle.setLastReadLocation(this.bookmark)
              BookmarkKind.BookmarkExplicit ->
                handle.setBookmarks(
                  BServiceBookmarks.normalizeBookmarks(
                    logger = this.logger,
                    profileId = this.profile.id,
                    handle = handle,
                    bookmark = bookmark
                  )
                )
            }

            this.bookmarkEventsOut.onNext(
              BookmarkEvent.BookmarkSaved(
                this.accountID,
                this.bookmark
              )
            )
          } else {
            this.logger.debug(
              "[{}]: unable to save bookmark; no format handle",
              this.profile.id.uuid
            )
          }
        }
        else ->
          throw IllegalStateException("Unsupported bookmark type: $bookmark")
      }
    } catch (e: Exception) {
      this.logger.error("error saving bookmark locally: ", e)
    }
  }
}
