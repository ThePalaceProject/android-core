package org.librarysimplified.viewer.audiobook

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime1
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime2
import org.nypl.simplified.books.api.bookmark.SerializedLocatorHrefProgression20210317
import org.nypl.simplified.books.api.bookmark.SerializedLocatorLegacyCFI
import org.nypl.simplified.books.api.bookmark.SerializedLocatorPage1
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry

/**
 * Functions to convert between SimplyE and SR2 bookmarks.
 */

object AudioBookBookmarks {

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
        readingOrderItemOffsetMilliseconds = source.offsetMilliseconds.value
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

        val readingOrderID =
          PlayerManifestReadingOrderID(location.readingOrderItem)
        val offsetMilliseconds =
          PlayerMillisecondsReadingOrderItem(location.readingOrderItemOffsetMilliseconds)

        PlayerBookmark(
          kind = kind,
          readingOrderID = readingOrderID,
          offsetMilliseconds = offsetMilliseconds,
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
