package org.librarysimplified.viewer.epub.readium2

import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Locator
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceUsableType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime1
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime2
import org.nypl.simplified.books.api.bookmark.SerializedLocatorHrefProgression20210317
import org.nypl.simplified.books.api.bookmark.SerializedLocatorLegacyCFI
import org.nypl.simplified.books.api.bookmark.SerializedLocatorPage1
import org.nypl.simplified.feeds.api.FeedEntry
import org.readium.r2.shared.publication.Href
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
  ): BookmarksForBook {
    return try {
      bookmarkService
        .bookmarkSyncAndLoad(accountID, bookID)
        .get(10L, TimeUnit.SECONDS)
    } catch (e: Exception) {
      this.logger.debug("Could not load bookmarks: ", e)
      try {
        bookmarkService.bookmarkLoad(
          accountID, bookID
        ).get(10L, TimeUnit.SECONDS)
      } catch (e: Exception) {
        this.logger.debug("Could not load bookmarks: ", e)
        BookmarksForBook(
          bookId = bookID,
          lastRead = null,
          bookmarks = listOf()
        )
      }
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
      rawBookmarks.lastRead?.let { this.toSR2Bookmark(it) }
    val explicits =
      rawBookmarks.bookmarks.mapNotNull { this.toSR2Bookmark(it) }

    val results = mutableListOf<SR2Bookmark>()
    lastReadLocal?.let(results::add)
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
  ): SerializedBookmark {
    val chapterHref =
      source.locator.chapterHref.toString()
    val chapterProgress =
      when (val locator = source.locator) {
        is SR2Locator.SR2LocatorPercent -> locator.chapterProgress
        is SR2Locator.SR2LocatorChapterEnd -> 1.0
      }

    val location =
      SerializedLocatorHrefProgression20210317(chapterHref, chapterProgress)

    val kind = when (source.type) {
      SR2Bookmark.Type.EXPLICIT ->
        BookmarkKind.BookmarkExplicit

      SR2Bookmark.Type.LAST_READ ->
        BookmarkKind.BookmarkLastReadLocation
    }

    return SerializedBookmarks.createWithCurrentFormat(
      bookChapterProgress = chapterProgress,
      bookChapterTitle = source.title,
      bookProgress = source.bookProgress ?: 0.0,
      bookTitle = bookEntry.feedEntry.title,
      deviceID = deviceId,
      kind = kind,
      location = location,
      opdsId = bookEntry.feedEntry.id,
      time = source.date,
      uri = source.uri,
    )
  }

  /**
   * Convert a SimplyE bookmark to an SR2 bookmark.
   */

  fun toSR2Bookmark(
    source: SerializedBookmark
  ): SR2Bookmark? {
    return when (val location = source.location) {
      is SerializedLocatorAudioBookTime1,
      is SerializedLocatorAudioBookTime2,
      is SerializedLocatorLegacyCFI,
      is SerializedLocatorPage1 -> {
        // None of these locator formats are suitable for EPUBs in R2
        null
      }

      is SerializedLocatorHrefProgression20210317 -> {
        val href = Href(location.chapterHref)
        if (href != null) {
          SR2Bookmark(
            date = source.time.toDateTime(),
            type = when (source.kind) {
              BookmarkKind.BookmarkLastReadLocation ->
                SR2Bookmark.Type.LAST_READ

              BookmarkKind.BookmarkExplicit ->
                SR2Bookmark.Type.EXPLICIT
            },
            title = source.bookChapterTitle,
            locator = SR2Locator.SR2LocatorPercent.create(
              chapterHref = href,
              chapterProgress = location.chapterProgress
            ),
            bookProgress = source.bookProgress,
            uri = source.uri
          )
        } else {
          null
        }
      }
    }
  }
}
