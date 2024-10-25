package org.librarysimplified.viewer.pdf.pdfjs

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceUsableType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind.BookmarkExplicit
import org.nypl.simplified.books.api.bookmark.BookmarkKind.BookmarkLastReadLocation
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime1
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime2
import org.nypl.simplified.books.api.bookmark.SerializedLocatorHrefProgression20210317
import org.nypl.simplified.books.api.bookmark.SerializedLocatorLegacyCFI
import org.nypl.simplified.books.api.bookmark.SerializedLocatorPage1
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

internal object PdfReaderBookmarks {

  private val logger =
    LoggerFactory.getLogger(PdfReaderBookmarks::class.java)

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
      this.logger.debug("Could not load bookmarks: ", e)
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
  ): List<PdfBookmark> {
    val rawBookmarks =
      this.loadRawBookmarks(
        bookmarkService = bookmarkService,
        accountID = accountID,
        bookID = bookID
      )

    val lastReadLocal =
      rawBookmarks.lastRead?.let { this.toPdfBookmark(it) }
    val explicits =
      rawBookmarks.bookmarks.mapNotNull { this.toPdfBookmark(it) }

    val results = mutableListOf<PdfBookmark>()
    lastReadLocal?.let(results::add)
    results.addAll(explicits)
    return results.toList()
  }

  /**
   * Convert a SimplyE bookmark to an SR2 bookmark.
   */

  fun toPdfBookmark(
    source: SerializedBookmark
  ): PdfBookmark? {
    return when (val location = source.location) {
      is SerializedLocatorAudioBookTime1,
      is SerializedLocatorAudioBookTime2,
      is SerializedLocatorLegacyCFI,
      is SerializedLocatorHrefProgression20210317 -> {
        // None of these locator formats are suitable for PDFs
        null
      }

      is SerializedLocatorPage1 -> {
        PdfBookmark(
          kind = when (source.kind) {
            BookmarkExplicit -> PdfBookmarkKind.EXPLICIT
            BookmarkLastReadLocation -> PdfBookmarkKind.LAST_READ
          },
          pageNumber = location.page,
          time = source.time
        )
      }
    }
  }

  /**
   * Convert a PDF bookmark to a SimplyE bookmark.
   */

  fun fromPdfBookmark(
    bookEntry: FeedEntry.FeedEntryOPDS,
    deviceId: String,
    source: PdfBookmark
  ): SerializedBookmark {
    return SerializedBookmarks.createWithCurrentFormat(
      bookChapterProgress = 0.0,
      bookChapterTitle = "",
      bookProgress = 0.0,
      bookTitle = bookEntry.feedEntry.title,
      deviceID = deviceId,
      kind = when (source.kind) {
        PdfBookmarkKind.EXPLICIT -> BookmarkExplicit
        PdfBookmarkKind.LAST_READ -> BookmarkLastReadLocation
      },
      location = SerializedLocatorPage1(source.pageNumber),
      opdsId = bookEntry.feedEntry.id,
      time = source.time,
      uri = null,
    )
  }
}
