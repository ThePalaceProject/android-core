package org.librarysimplified.viewer.audiobook

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
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
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Functions to convert between SimplyE and SR2 bookmarks.
 */

object AudioBookBookmarks {

  private val logger =
    LoggerFactory.getLogger(AudioBookBookmarks::class.java)

  private fun loadRawBookmarks(
    bookmarkService: BookmarkServiceUsableType,
    accountID: AccountID,
    bookID: BookID
  ): BookmarksForBook {
    return try {
      bookmarkService
        .bookmarkSyncAndLoad(accountID, bookID)
        .get(15L, TimeUnit.SECONDS)
    } catch (e: Exception) {
      this.logger.error("could not load bookmarks: ", e)
      BookmarksForBook(bookID, null, emptyList())
    }
  }

  /**
   * Load bookmarks from the given bookmark service.
   */

  fun loadBookmarks(
    bookmarkService: BookmarkServiceUsableType,
    accountID: AccountID,
    bookID: BookID
  ): List<PlayerBookmark> {
    val rawBookmarks =
      this.loadRawBookmarks(
        bookmarkService = bookmarkService,
        accountID = accountID,
        bookID = bookID
      )

    val lastReadLocal =
      rawBookmarks.lastRead?.let { this.toPlayerBookmark(it) }
    val explicits =
      rawBookmarks.bookmarks.mapNotNull { this.toPlayerBookmark(it) }

    val results = mutableListOf<PlayerBookmark>()
    lastReadLocal?.let(results::add)
    results.addAll(explicits)
    return results.toList()
  }

  /**
   * Convert a Player bookmark to a SimplyE bookmark.
   */

  fun fromPlayerBookmark(
    feedEntry: OPDSAcquisitionFeedEntry,
    deviceId: String,
    source: PlayerBookmark
  ): SerializedBookmark {
    val kind =
      when (source.kind) {
        PlayerBookmarkKind.EXPLICIT -> BookmarkKind.BookmarkExplicit
        PlayerBookmarkKind.LAST_READ -> BookmarkKind.BookmarkLastReadLocation
      }

    return SerializedBookmarks.createWithCurrentFormat(
      bookTitle = feedEntry.title,
      deviceID = deviceId,
      kind = kind,
      opdsId = feedEntry.id,
      time = source.metadata.creationTime,
      bookChapterProgress = source.metadata.chapterProgressEstimate,
      bookChapterTitle = source.metadata.chapterTitle,
      bookProgress = source.metadata.bookProgressEstimate,
      location = SerializedLocatorAudioBookTime2(
        readingOrderItem = source.readingOrderID.text,
        readingOrderItemOffsetMilliseconds = source.offsetMilliseconds
      ),
      uri = null
    )
  }

  /**
   * Convert a SimplyE bookmark to a Player bookmark.
   */

  fun toPlayerBookmark(
    source: SerializedBookmark
  ): PlayerBookmark? {
    return when (val location = source.location) {
      is SerializedLocatorAudioBookTime1 -> {
        null
      }

      is SerializedLocatorAudioBookTime2 -> {
        val kind: PlayerBookmarkKind =
          when (source.kind) {
            BookmarkKind.BookmarkExplicit -> PlayerBookmarkKind.EXPLICIT
            BookmarkKind.BookmarkLastReadLocation -> PlayerBookmarkKind.LAST_READ
          }
        PlayerBookmark(
          kind = kind,
          readingOrderID = PlayerManifestReadingOrderID(location.readingOrderItem),
          offsetMilliseconds = location.readingOrderItemOffsetMilliseconds,
          metadata = PlayerBookmarkMetadata(
            creationTime = source.time,
            chapterTitle = source.bookChapterTitle,
            totalRemainingBookTime = Duration.ZERO,
            chapterProgressEstimate = source.bookChapterProgress,
            bookProgressEstimate = source.bookProgress
          )
        )
      }

      is SerializedLocatorLegacyCFI,
      is SerializedLocatorPage1,
      is SerializedLocatorHrefProgression20210317 -> {
        // None of these locator formats are suitable for audio books.
        null
      }
    }
  }
}
