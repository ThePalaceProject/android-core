package org.nypl.simplified.viewer.epub.readium2

import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Locator
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceUsableType
import org.nypl.simplified.bookmarks.api.Bookmarks
import org.nypl.simplified.books.api.BookChapterProgress
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookLocation
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Functions to convert between SimplyE and SR2 bookmarks.
 */

object Reader2Bookmarks {

  private val logger =
    LoggerFactory.getLogger(Reader2Bookmarks::class.java)

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
  ): List<SR2Bookmark> {
    val rawBookmarks =
      this.loadRawBookmarks(
        bookmarkService = bookmarkService,
        accountID = accountID,
        bookID = bookID
      )
    val lastReadLocal =
      rawBookmarks.lastReadLocal?.let { this.toSR2Bookmark(it) }
    val lastReadServer =
      rawBookmarks.lastReadServer?.let { this.toSR2Bookmark(it) }
    val explicits =
      rawBookmarks.bookmarks.mapNotNull { this.toSR2Bookmark(it) }

    val results = mutableListOf<SR2Bookmark>()
    lastReadLocal?.let(results::add)
    lastReadServer?.let(results::add)
    results.addAll(explicits)
    return results.toList()
  }

  /**
   * Convert an SR2 bookmark to a SimplyE bookmark.
   */

  fun fromSR2Bookmark(
    bookEntry: FeedEntry.FeedEntryOPDS,
    deviceId: String,
    source: SR2Bookmark
  ): Bookmark.ReaderBookmark {
    val progress = BookChapterProgress(
      chapterHref = source.locator.chapterHref,
      chapterProgress = when (val locator = source.locator) {
        is SR2Locator.SR2LocatorPercent -> locator.chapterProgress
        is SR2Locator.SR2LocatorChapterEnd -> 1.0
      }
    )

    val location =
      BookLocation.BookLocationR2(progress)

    val kind = when (source.type) {
      SR2Bookmark.Type.EXPLICIT ->
        BookmarkKind.BookmarkExplicit
      SR2Bookmark.Type.LAST_READ ->
        BookmarkKind.BookmarkLastReadLocation
    }

    return Bookmark.ReaderBookmark.create(
      opdsId = bookEntry.feedEntry.id,
      location = location,
      time = source.date,
      kind = kind,
      chapterTitle = source.title,
      bookProgress = source.bookProgress,
      deviceID = deviceId,
      uri = source.uri
    )
  }

  /**
   * Convert a SimplyE bookmark to an SR2 bookmark.
   */

  fun toSR2Bookmark(
    source: Bookmark
  ): SR2Bookmark? {
    if (source !is Bookmark.ReaderBookmark) {
      throw IllegalStateException("Unsupported type of bookmark: $source")
    }
    return when (val location = source.location) {
      is BookLocation.BookLocationR2 ->
        this.r2ToSR2Bookmark(source, location)
      is BookLocation.BookLocationR1 ->
        null // R1 bookmarks are not supported any more.
    }
  }

  private fun r2ToSR2Bookmark(
    source: Bookmark.ReaderBookmark,
    location: BookLocation.BookLocationR2
  ): SR2Bookmark =
    SR2Bookmark(
      date = source.time.toDateTime(),
      type = when (source.kind) {
        BookmarkKind.BookmarkLastReadLocation ->
          SR2Bookmark.Type.LAST_READ
        BookmarkKind.BookmarkExplicit ->
          SR2Bookmark.Type.EXPLICIT
      },
      title = source.chapterTitle,
      locator = SR2Locator.SR2LocatorPercent(
        chapterHref = location.progress.chapterHref,
        chapterProgress = location.progress.chapterProgress
      ),
      bookProgress = source.bookProgress,
      uri = source.uri
    )
}
