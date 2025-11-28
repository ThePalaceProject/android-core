package org.nypl.simplified.bookmarks.api

/**
 * The bookmark service interface.
 */

interface BookmarkServiceType : AutoCloseable, BookmarkServiceUsableType {

  override fun close()
}
