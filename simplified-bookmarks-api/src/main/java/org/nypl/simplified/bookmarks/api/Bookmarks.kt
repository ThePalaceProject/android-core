package org.nypl.simplified.bookmarks.api

import org.nypl.simplified.books.api.bookmark.Bookmark
import java.io.Serializable

/**
 * A set of bookmarks.
 *
 * <p>Note: The type is {@link Serializable} purely because the Android API requires this
 * in order pass values of this type between activities. We make absolutely no guarantees
 * that serialized values of this class will be compatible with future releases.</p>
 */

data class Bookmarks(
  val lastReadLocal: Bookmark?,
  val lastReadServer: Bookmark?,
  val bookmarks: List<Bookmark>
) : Serializable
