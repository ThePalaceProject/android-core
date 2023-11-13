package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.Bookmarks
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
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
  private val bookID: BookID?
) : BServiceOp<Bookmark?>(logger) {

  override fun runActual(): Bookmark? {
    this.logger.debug(
      "[{}]: syncing account {}",
      this.profile.id.uuid,
      this.accountID
    )

    val syncable =
      BSyncableAccount.ofAccount(this.profile.account(this.accountID))

    if (syncable == null) {
      this.logger.error("[{}]: account no longer syncable", this.accountID.uuid)
      return null
    }

    try {
      BServiceOpCheckSyncStatusForAccount(
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
      return null
    }

    val received = this.readBookmarksFromServer(syncable, bookID)
    this.sendBookmarksToServer(syncable, received.bookmarks)
    this.bookmarkEventsOut.onNext(BookmarkEvent.BookmarkSyncFinished(syncable.account.id))

    return received.lastReadServer
  }

  private fun sendBookmarksToServer(
    syncable: BSyncableAccount,
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

        val bookmarkAnnotation = when (bookmark) {
          is Bookmark.ReaderBookmark -> {
            BookmarkAnnotations.fromReaderBookmark(this.objectMapper, bookmark)
          }
          is Bookmark.AudiobookBookmark -> {
            BookmarkAnnotations.fromAudiobookBookmark(this.objectMapper, bookmark)
          }
          is Bookmark.PDFBookmark -> {
            BookmarkAnnotations.fromPdfBookmark(this.objectMapper, bookmark)
          }
          else -> {
            throw IllegalStateException("Unsupported bookmark type: $bookmark")
          }
        }

        this.httpCalls.bookmarkAdd(
          account = syncable.account,
          annotationsURI = syncable.annotationsURI,
          credentials = syncable.credentials,
          bookmark = bookmarkAnnotation
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
    syncable: BSyncableAccount
  ): Set<Bookmark> {
    return syncable.account.bookDatabase.books()
      .map { id -> syncable.account.bookDatabase.entry(id) }
      .mapNotNull { entry -> entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java) }
      .flatMap { handle -> handle.format.bookmarks }
      .filter { bookmark -> bookmark.kind == BookmarkKind.BookmarkExplicit }
      .filterNot { bookmark -> received.any { remote -> remote.bookmarkId == bookmark.bookmarkId } }
      .toSet()
  }

  private fun readBookmarksFromServer(
    syncable: BSyncableAccount,
    bookID: BookID?
  ): Bookmarks {
    this.bookmarkEventsOut.onNext(BookmarkEvent.BookmarkSyncStarted(syncable.account.id))

    val bookmarks: List<Bookmark> =
      try {
        val annotations = this.httpCalls.bookmarksGet(
          account = syncable.account,
          annotationsURI = syncable.annotationsURI,
          credentials = syncable.credentials
        )
        annotations.mapNotNull(this::parseBookmarkOrNull)
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

    var serverLastReadBookmark: Bookmark? = null

    for (bookmark in bookmarks) {
      try {
        this.logger.debug(
          "[{}]: received bookmark {}",
          this.profile.id.uuid,
          bookmark.bookmarkId.value
        )

        if (!syncable.account.bookDatabase.books().contains(bookmark.book)) {
          this.logger.debug(
            "[{}]: we no longer have book {}",
            this.profile.id.uuid,
            bookmark.book.value()
          )
          continue
        }

        val entry = syncable.account.bookDatabase.entry(bookmark.book)

        when (bookmark) {
          is Bookmark.ReaderBookmark -> {
            val handle = entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
            if (handle != null) {
              when (bookmark.kind) {
                BookmarkKind.BookmarkLastReadLocation -> {
                  // check if it's the last read bookmark for the given book ID
                  if (bookID != null && bookmark.book == bookID) {
                    serverLastReadBookmark = bookmark
                  } else {
                    handle.setLastReadLocation(bookmark)
                  }
                }
                BookmarkKind.BookmarkExplicit -> {
                  handle.setBookmarks(
                    BServiceBookmarks.normalizeBookmarks(
                      logger = this.logger,
                      profileId = this.profile.id,
                      handle = handle,
                      bookmark = bookmark
                    )
                  )
                }
              }

              this.bookmarkEventsOut.onNext(
                BookmarkEvent.BookmarkSaved(
                  syncable.account.id,
                  bookmark
                )
              )
            }
          }
          is Bookmark.PDFBookmark -> {
            val handle = entry.findFormatHandle(
              BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF::class.java
            )
            if (handle != null) {
              when (bookmark.kind) {
                BookmarkKind.BookmarkLastReadLocation -> {
                  // check if it's the last read bookmark for the given book ID
                  if (bookID != null && bookmark.book == bookID) {
                    serverLastReadBookmark = bookmark
                  } else {
                    handle.setLastReadLocation(bookmark)
                  }
                }
                BookmarkKind.BookmarkExplicit -> {
                  handle.setBookmarks(
                    BServiceBookmarks.normalizeBookmarks(
                      logger = this.logger,
                      profileId = this.profile.id,
                      handle = handle,
                      bookmark = bookmark
                    )
                  )
                }
              }

              this.bookmarkEventsOut.onNext(
                BookmarkEvent.BookmarkSaved(
                  syncable.account.id,
                  bookmark
                )
              )
            }
          }
          is Bookmark.AudiobookBookmark -> {
            val handle =
              entry.findFormatHandle(
                BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook::class.java
              )
            if (handle != null) {
              when (bookmark.kind) {
                BookmarkKind.BookmarkLastReadLocation -> {
                  handle.setLastReadLocation(bookmark)
                }
                BookmarkKind.BookmarkExplicit -> {
                  handle.setBookmarks(
                    BServiceBookmarks.normalizeBookmarks(
                      logger = this.logger,
                      profileId = this.profile.id,
                      handle = handle,
                      bookmark = bookmark
                    )
                  )
                }
              }
              this.bookmarkEventsOut.onNext(
                BookmarkEvent.BookmarkSaved(
                  syncable.account.id,
                  bookmark
                )
              )
            }
          }
          else ->
            throw IllegalStateException("Unsupported bookmark type: $bookmark")
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

    return Bookmarks(
      lastReadLocal = null,
      lastReadServer = serverLastReadBookmark,
      bookmarks = bookmarks
    )
  }

  private fun parseBookmarkOrNull(
    annotation: BookmarkAnnotation
  ): Bookmark? {
    return try {
      val bookmark = BookmarkAnnotations.toAudiobookBookmark(this.objectMapper, annotation)
      this.logger.debug("Audiobook bookmark successfully parsed")
      bookmark
    } catch (e: Exception) {
      try {
        val bookmark = BookmarkAnnotations.toReaderBookmark(this.objectMapper, annotation)
        this.logger.debug("Reader bookmark successfully parsed")
        bookmark
      } catch (e: Exception) {
        try {
          val bookmark = BookmarkAnnotations.toPdfBookmark(this.objectMapper, annotation)
          this.logger.debug("PDF bookmark successfully parsed")
          bookmark
        } catch (e: Exception) {
          this.logger.error("unable to parse bookmark: ", e)
          null
        }
      }
    }
  }
}
