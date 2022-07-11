package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.profiles.api.ProfileID
import org.slf4j.Logger

internal object BServiceBookmarks {

  /**
   * Normalize the set of bookmarks in the book database. This is necessary because bookmarks
   * do not really have identities and we have to manually deduplicate them if we happen to
   * notice that two bookmarks are the same. Bookmarks have a manually calculated "identity"
   * represented by the [Bookmark.bookmarkId] property, and we can use this to effectively
   * deduplicate bookmarks.
   */

  fun normalizeBookmarks(
    logger: Logger,
    profileId: ProfileID,
    handle: BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB,
    bookmark: Bookmark.ReaderBookmark
  ): List<Bookmark.ReaderBookmark> {
    val originalBookmarks =
      handle.format.bookmarks
    val bookmarksById =
      originalBookmarks.associateBy { mark -> mark.bookmarkId }
        .toMutableMap()

    bookmarksById[bookmark.bookmarkId] = bookmark

    logger.debug(
      "[{}]: normalized {} -> {} bookmarks",
      profileId.uuid,
      originalBookmarks.size,
      bookmarksById.size
    )

    return bookmarksById.values.toList()
  }

  fun normalizeBookmarks(
    logger: Logger,
    profileId: ProfileID,
    handle: BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook,
    bookmark: Bookmark.AudiobookBookmark
  ): List<Bookmark.AudiobookBookmark> {
    val originalBookmarks =
      handle.format.bookmarks
    val bookmarksById =
      originalBookmarks.associateBy { mark -> mark.bookmarkId }
        .toMutableMap()

    bookmarksById[bookmark.bookmarkId] = bookmark

    logger.debug(
      "[{}]: normalized {} -> {} bookmarks",
      profileId.uuid,
      originalBookmarks.size,
      bookmarksById.size
    )

    return bookmarksById.values.toList()
  }
}
