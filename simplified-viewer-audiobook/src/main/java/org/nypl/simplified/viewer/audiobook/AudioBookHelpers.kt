package org.nypl.simplified.viewer.audiobook

import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceUsableType
import org.nypl.simplified.bookmarks.api.Bookmarks
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

internal object AudioBookHelpers {

  private val logger =
    LoggerFactory.getLogger(AudioBookHelpers::class.java)

  /**
   * Attempt to save a manifest in the books database.
   */

  fun saveManifest(
    profiles: ProfilesControllerType,
    bookId: BookID,
    manifestURI: URI,
    manifest: ManifestFulfilled
  ) {
    val handle =
      profiles.profileAccountForBook(bookId)
        .bookDatabase
        .entry(bookId)
        .findFormatHandle(BookDatabaseEntryFormatHandleAudioBook::class.java)

    val contentType = manifest.contentType
    if (handle == null) {
      this.logger.error(
        "Bug: Book database entry has no audio book format handle", IllegalStateException()
      )
      return
    }

    if (!handle.formatDefinition.supports(contentType)) {
      this.logger.error(
        "Server delivered an unsupported content type: {}: ", contentType, IOException()
      )
      return
    }

    handle.copyInManifestAndURI(manifest.data, manifestURI)
  }

  private fun loadRawBookmarks(
    bookmarkService: BookmarkServiceUsableType,
    accountID: AccountID,
    bookID: BookID
  ): Bookmarks {
    return try {
      bookmarkService
        .bookmarkSyncAndLoad(accountID, bookID)
        .get(15L, TimeUnit.SECONDS)
    } catch (e: Exception) {
      this.logger.error("could not load bookmarks: ", e)
      Bookmarks(null, null, emptyList())
    }
  }

  /**
   * Load bookmarks from the given bookmark service.
   */

  fun loadBookmarks(
    bookmarkService: BookmarkServiceUsableType,
    accountID: AccountID,
    bookID: BookID
  ): List<Bookmark> {
    val rawBookmarks =
      this.loadRawBookmarks(
        bookmarkService = bookmarkService,
        accountID = accountID,
        bookID = bookID
      )

    // for now we'll keep the existing behavior and ignore the "lastReadServer" field that is always
    // null for the audiobook bookmarks
    val lastRead = rawBookmarks.lastReadLocal
    val explicits = rawBookmarks.bookmarks

    val results = mutableListOf<Bookmark>()
    lastRead?.let(results::add)
    results.addAll(explicits)
    return results.toList()
  }

  fun toPlayerBookmark(bookmark: Bookmark.AudiobookBookmark): PlayerBookmark {
    return PlayerBookmark(
      date = bookmark.time,
      position = bookmark.location,
      duration = bookmark.duration,
      uri = bookmark.uri
    )
  }

  fun fromPlayerBookmark(
    opdsId: String,
    deviceID: String,
    playerBookmark: PlayerBookmark
  ): Bookmark.AudiobookBookmark {
    return Bookmark.AudiobookBookmark(
      opdsId = opdsId,
      deviceID = deviceID,
      time = playerBookmark.date,
      kind = BookmarkKind.BookmarkExplicit,
      uri = playerBookmark.uri,
      location = playerBookmark.position,
      duration = playerBookmark.duration
    )
  }
}
