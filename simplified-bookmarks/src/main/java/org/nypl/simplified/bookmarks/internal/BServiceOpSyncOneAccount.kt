package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.io7m.jattribute.core.AttributeType
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that synchronizes bookmarks for all accounts that want it.
 */

internal class BServiceOpSyncOneAccount(
  logger: Logger,
  private val httpCalls: BookmarkHTTPCallsType,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val objectMapper: ObjectMapper,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<List<SerializedBookmark>>(logger) {

  override fun runActual(): List<SerializedBookmark> {
    this.logger.debug(
      "[{}]: syncing account {}",
      this.profile.id.uuid,
      this.accountID
    )

    val syncable =
      BSyncableAccount.ofAccount(this.profile.account(this.accountID))

    if (syncable == null) {
      this.logger.error("[{}]: account no longer syncable", this.accountID.uuid)
      return listOf()
    }

    if (!syncable.account.preferences.bookmarkSyncingPermitted) {
      this.logger.debug("[{}]: syncing not permitted", this.accountID.uuid)
      return listOf()
    }

    val received = this.readBookmarksFromServer(syncable)
    this.sendBookmarksToServer(syncable, received)
    this.bookmarkEventsOut.onNext(BookmarkEvent.BookmarkSyncFinished(syncable.account.id))
    return received
  }

  private fun sendBookmarksToServer(
    syncable: BSyncableAccount,
    received: List<SerializedBookmark>
  ) {
    val localExtras =
      this.determineExtraLocalBookmarks(received, syncable)

    this.logger.debug(
      "[{}]: We have {} bookmarks the server did not have",
      this.accountID.uuid,
      localExtras.size
    )

    for (bookmark in localExtras) {
      try {
        this.logger.debug(
          "[{}]: Sending bookmark {}",
          this.accountID.uuid,
          bookmark.bookmarkId.value
        )

        val bookmarkAnnotation =
          BookmarkAnnotations.fromSerializedBookmark(this.objectMapper, bookmark)

        this.httpCalls.bookmarkAdd(
          account = syncable.account,
          annotationsURI = syncable.annotationsURI,
          credentials = syncable.credentials,
          bookmark = bookmarkAnnotation
        )
      } catch (e: Exception) {
        this.logger.error("[{}]: Error sending bookmark: ", this.accountID.uuid, e)
      }
    }
  }

  /*
   * Determine which bookmarks we have locally that weren't in the set of bookmarks recently
   * received from the server.
   */

  private fun determineExtraLocalBookmarks(
    received: List<SerializedBookmark>,
    syncable: BSyncableAccount
  ): Set<SerializedBookmark> {
    return syncable.account.bookDatabase.books()
      .map { id -> syncable.account.bookDatabase.entry(id) }
      .mapNotNull { entry -> entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java) }
      .flatMap { handle -> handle.format.bookmarks }
      .filter { bookmark -> bookmark.kind == BookmarkKind.BookmarkExplicit }
      .filterNot { bookmark -> received.any { remote -> remote.bookmarkId == bookmark.bookmarkId } }
      .toSet()
  }

  private fun readBookmarksFromServer(
    syncable: BSyncableAccount
  ): List<SerializedBookmark> {
    this.bookmarkEventsOut.onNext(BookmarkEvent.BookmarkSyncStarted(syncable.account.id))

    val bookmarkAnnotations: List<BookmarkAnnotation> =
      try {
        this.httpCalls.bookmarksGet(
          account = syncable.account,
          annotationsURI = syncable.annotationsURI,
          credentials = syncable.credentials
        )
      } catch (e: Exception) {
        this.logger.error(
          "[{}]: Could not receive bookmarks for account {}: ",
          this.profile.id.uuid,
          syncable.account.id,
          e
        )
        listOf()
      }

    this.logger.debug(
      "[{}]: Received {} bookmarks",
      this.profile.id.uuid,
      bookmarkAnnotations.size
    )

    val results = arrayListOf<SerializedBookmark>()

    for (bookmarkAnnotation in bookmarkAnnotations) {
      try {
        val bookmark =
          BookmarkAnnotations.toSerializedBookmark(this.objectMapper, bookmarkAnnotation)

        this.logger.debug(
          "[{}]: Received bookmark {}",
          this.profile.id.uuid,
          bookmark.bookmarkId.value
        )

        if (!syncable.account.bookDatabase.books().contains(bookmark.book)) {
          this.logger.debug(
            "[{}]: We no longer have book {}",
            this.profile.id.uuid,
            bookmark.book.value()
          )
          continue
        }

        val entry = syncable.account.bookDatabase.entry(bookmark.book)
        for (handle in entry.formatHandles) {
          when (bookmark.kind) {
            BookmarkKind.BookmarkExplicit -> {
              handle.addBookmark(bookmark)
            }
            BookmarkKind.BookmarkLastReadLocation -> {
              handle.setLastReadLocation(bookmark)
            }
          }

          this.bookmarksSource.set(
            BookmarkAttributes.addBookmark(
              this.bookmarksSource.get(),
              syncable.account.id,
              bookmark
            )
          )
          this.bookmarkEventsOut.onNext(
            BookmarkEvent.BookmarkSaved(
              syncable.account.id,
              bookmark
            )
          )
        }

        results.add(bookmark)
      } catch (e: Exception) {
        this.logger.error(
          "[{}]: could not store bookmark for account {}: ",
          this.profile.id.uuid,
          syncable.account.id,
          e
        )
      }
    }

    return results.toList()
  }
}
