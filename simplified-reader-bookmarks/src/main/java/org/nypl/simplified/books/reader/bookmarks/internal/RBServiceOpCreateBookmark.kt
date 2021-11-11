package org.nypl.simplified.books.reader.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Bookmark
import org.nypl.simplified.books.api.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent.ReaderBookmarkSaved
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks.
 */

internal class RBServiceOpCreateBookmark(
  logger: Logger,
  private val objectMapper: ObjectMapper,
  private val bookmarkEventsOut: Subject<ReaderBookmarkEvent>,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: Bookmark
) : RBServiceOp<Unit>(logger) {

  override fun runActual() {
    this.locallySaveBookmark()
    this.remotelySendBookmark()
  }

  private fun remotelySendBookmark() {
    try {
      this.logger.debug(
        "[{}]: remote sending bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val syncInfo = RBSyncableAccount.ofAccount(this.profile.account(this.accountID))
      if (syncInfo == null) {
        this.logger.debug(
          "[{}]: cannot remotely send bookmark {} because the account is not syncable",
          this.profile.id.uuid,
          this.bookmark.bookmarkId.value
        )
        return
      }

      this.httpCalls.bookmarkAdd(
        annotationsURI = syncInfo.annotationsURI,
        credentials = syncInfo.credentials,
        bookmark = BookmarkAnnotations.fromBookmark(this.objectMapper, this.bookmark)
      )
    } catch (e: Exception) {
      this.logger.error("error sending bookmark: ", e)
    }
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
      val handle =
        entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)

      if (handle != null) {
        when (this.bookmark.kind) {
          BookmarkKind.ReaderBookmarkLastReadLocation ->
            handle.setLastReadLocation(this.bookmark)
          BookmarkKind.ReaderBookmarkExplicit ->
            handle.setBookmarks(
              RBServiceBookmarks.normalizeBookmarks(
                logger = this.logger,
                profileId = this.profile.id,
                handle = handle,
                bookmark = bookmark
              )
            )
        }

        this.bookmarkEventsOut.onNext(ReaderBookmarkSaved(this.accountID, this.bookmark))
      } else {
        this.logger.debug("[{}]: unable to save bookmark; no format handle", this.profile.id.uuid)
      }
    } catch (e: Exception) {
      this.logger.error("error saving bookmark locally: ", e)
    }
  }
}
