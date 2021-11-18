package org.nypl.simplified.books.reader.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Bookmark
import org.nypl.simplified.books.api.BookmarkKind.ReaderBookmarkExplicit
import org.nypl.simplified.books.api.BookmarkKind.ReaderBookmarkLastReadLocation
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.reader.bookmarks.internal.RBServiceBookmarks.normalizeBookmarks
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.reader.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent.ReaderBookmarkSaved
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent.ReaderBookmarkSyncFinished
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent.ReaderBookmarkSyncStarted
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.slf4j.Logger

/**
 * An operation that synchronizes bookmarks for all accounts that want it.
 */

internal class RBServiceOpSyncOneAccount(
  logger: Logger,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val bookmarkEventsOut: Subject<ReaderBookmarkEvent>,
  private val objectMapper: ObjectMapper,
  private val profile: ProfileReadableType,
  private val accountID: AccountID
) : RBServiceOp<Unit>(logger) {

  override fun runActual() {
    this.logger.debug(
      "[{}]: syncing account {}",
      this.profile.id.uuid,
      this.accountID
    )

    val syncable =
      RBSyncableAccount.ofAccount(this.profile.account(this.accountID))

    if (syncable == null) {
      this.logger.error("[{}]: account no longer syncable", this.accountID.uuid)
      return
    }

    try {
      RBServiceOpCheckSyncStatusForAccount(
        logger = this.logger,
        httpCalls = this.httpCalls,
        profile = this.profile,
        syncableAccount = syncable
      ).runActual()
    } catch (e: Exception) {
      this.logger.error("[{}]: failed to check sync status: ", this.accountID.uuid, e)
    }

    if (!syncable.account.preferences.bookmarkSyncingPermitted) {
      this.logger.debug("[{}]: syncing not permitted", this.accountID.uuid)
      return
    }

    val received = this.readBookmarksFromServer(syncable)
    this.sendBookmarksToServer(syncable, received)
    this.bookmarkEventsOut.onNext(ReaderBookmarkSyncFinished(syncable.account.id))
  }

  private fun sendBookmarksToServer(
    syncable: RBSyncableAccount,
    received: List<Bookmark>
  ) {
    val localExtras =
      this.determineExtraLocalBookmarks(received, syncable)

    this.logger.debug(
      "[{}]: we have {} bookmarks the server did not have",
      this.accountID.uuid,
      localExtras.size
    )

    for (bookmark in localExtras) {
      try {
        this.logger.debug(
          "[{}]: sending bookmark {}",
          this.accountID.uuid,
          bookmark.bookmarkId.value
        )

        this.httpCalls.bookmarkAdd(
          annotationsURI = syncable.annotationsURI,
          credentials = syncable.credentials,
          bookmark = BookmarkAnnotations.fromBookmark(this.objectMapper, bookmark)
        )
      } catch (e: Exception) {
        this.logger.error("[{}]: error sending bookmark: ", this.accountID.uuid, e)
      }
    }
  }

  /*
   * Determine which bookmarks we have locally that weren't in the set of bookmarks recently
   * received from the server.
   */

  private fun determineExtraLocalBookmarks(
    received: List<Bookmark>,
    syncable: RBSyncableAccount
  ): Set<Bookmark> {
    return syncable.account.bookDatabase.books()
      .map { id -> syncable.account.bookDatabase.entry(id) }
      .mapNotNull { entry -> entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java) }
      .flatMap { handle -> handle.format.bookmarks }
      .filter { bookmark -> bookmark.kind == ReaderBookmarkExplicit }
      .filterNot { bookmark -> received.any { remote -> remote.bookmarkId == bookmark.bookmarkId } }
      .toSet()
  }

  private fun readBookmarksFromServer(
    syncable: RBSyncableAccount
  ): List<Bookmark> {
    this.bookmarkEventsOut.onNext(ReaderBookmarkSyncStarted(syncable.account.id))

    val bookmarks: List<Bookmark> =
      try {
        this.httpCalls.bookmarksGet(syncable.annotationsURI, syncable.credentials)
          .mapNotNull(this::parseBookmarkOrNull)
      } catch (e: Exception) {
        this.logger.error(
          "[{}]: could not receive bookmarks for account {}: ",
          this.profile.id.uuid,
          syncable.account.id,
          e
        )
        listOf()
      }

    this.logger.debug("[{}]: received {} bookmarks", this.profile.id.uuid, bookmarks.size)
    for (bookmark in bookmarks) {
      try {
        this.logger.debug(
          "[{}]: received bookmark {}",
          this.profile.id.uuid,
          bookmark.bookmarkId.value
        )

        val entry = syncable.account.bookDatabase.entry(bookmark.book)
        val handle = entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
        if (handle != null) {
          when (bookmark.kind) {
            ReaderBookmarkLastReadLocation ->
              handle.setLastReadLocation(bookmark)
            ReaderBookmarkExplicit ->
              handle.setBookmarks(
                normalizeBookmarks(
                  logger = this.logger,
                  profileId = this.profile.id,
                  handle = handle,
                  bookmark = bookmark
                )
              )
          }
          this.bookmarkEventsOut.onNext(ReaderBookmarkSaved(syncable.account.id, bookmark))
        }
      } catch (e: Exception) {
        this.logger.error(
          "[{}]: could not store bookmark for account {}: ",
          this.profile.id.uuid,
          syncable.account.id,
          e
        )
      }
    }
    return bookmarks
  }

  private fun parseBookmarkOrNull(
    annotation: BookmarkAnnotation
  ): Bookmark? {
    return try {
      BookmarkAnnotations.toBookmark(this.objectMapper, annotation)
    } catch (e: Exception) {
      this.logger.error("unable to parse bookmark: ", e)
      null
    }
  }
}
