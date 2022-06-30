package org.nypl.simplified.viewer.audiobook

import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceUsableType
import org.nypl.simplified.bookmarks.api.Bookmarks
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
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
      Bookmarks(null, emptyList())
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
    val lastRead = rawBookmarks.lastRead
    val explicits = rawBookmarks.bookmarks

    val results = mutableListOf<Bookmark>()
    lastRead?.let(results::add)
    results.addAll(explicits)
    return results.toList()
  }
}
