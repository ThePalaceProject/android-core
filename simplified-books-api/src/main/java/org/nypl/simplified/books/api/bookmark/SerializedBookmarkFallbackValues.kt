package org.nypl.simplified.books.api.bookmark

/**
 * Fallback values used when trying to parse bookmarks in locators in ancient formats that
 * provided less information than is now required in modern formats.
 */

data class SerializedBookmarkFallbackValues(
  val kind: BookmarkKind
)
