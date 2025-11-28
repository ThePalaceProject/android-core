package org.nypl.simplified.books.api.bookmark

/**
 * Fallback values used when trying to parse bookmarks in locators in ancient formats that
 * provided less information than is now required in modern formats.
 */

data class SerializedBookmarkFallbackValues(

  /**
   * The bookmark kind.
   */

  val kind: BookmarkKind,

  /**
   * The book's OPDS ID as it appeared in an OPDS feed.
   */

  val bookOPDSId: String,

  /**
   * The book's title as it appeared in an OPDS feed.
   */

  val bookTitle: String
)
