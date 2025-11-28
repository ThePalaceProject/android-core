package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime
import org.nypl.simplified.books.api.BookID
import java.net.URI
import java.security.MessageDigest

/**
 * The type of serialized bookmarks.
 */

sealed class SerializedBookmark {

  /**
   * The type name (such as "Bookmark")
   */

  abstract val typeName: String

  /**
   * The type version (such as 20210317)
   */

  abstract val typeVersion: Int

  /**
   * @return The identifier of the book taken from the OPDS entry that provided it.
   */

  abstract val opdsId: String

  /**
   * @return The kind of bookmark.
   */

  abstract val kind: BookmarkKind

  /**
   * @return The time the bookmark was created.
   */

  abstract val time: DateTime

  /**
   * @return The identifier of the device that created the bookmark, if one is available.
   */

  abstract val deviceID: String

  /**
   * @return The URI of this bookmark, if the bookmark exists on a remote server.
   */

  abstract val uri: URI?

  /**
   * The ID of the book to which the bookmark belongs.
   */

  abstract val book: BookID

  /**
   * The unique ID of the bookmark.
   */

  abstract val bookmarkId: BookmarkID

  /**
   * The bookmark location
   */

  abstract val location: SerializedLocator

  /**
   * The progress of the bookmark throughout the entire book
   */

  abstract val bookProgress: Double

  /**
   * The progress of the bookmark throughout the chapter
   */

  abstract val bookChapterProgress: Double

  /**
   * The book title
   */

  abstract val bookTitle: String

  /**
   * The book chapter title
   */

  abstract val bookChapterTitle: String

  /**
   * Serialize this bookmark to JSON.
   */

  abstract fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode

  /**
   * Add the fields of this object to the given message digest
   */

  abstract fun addToDigest(digest: MessageDigest)

  /**
   * @return This bookmark with the given URI
   */

  abstract fun withURI(uri: URI): SerializedBookmark

  /**
   * @return This bookmark without the URI
   */

  abstract fun withoutURI(): SerializedBookmark

  /**
   * @return true if this bookmark can be used in place of the other bookmark.
   */

  fun isInterchangeableWith(
    bookmark: SerializedBookmark
  ): Boolean {
    if (this == bookmark) {
      return true
    }
    if (this.withoutURI() == bookmark.withoutURI()) {
      return true
    }
    return false
  }
}
