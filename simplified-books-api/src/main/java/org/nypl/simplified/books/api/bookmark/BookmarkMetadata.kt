package org.nypl.simplified.books.api.bookmark

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.net.URI
import java.security.MessageDigest

/**
 * The non-critical metadata for a bookmark.
 */

data class BookmarkMetadata(
  val bookChapterProgress: Double,
  val bookChapterTitle: String,
  val bookOpdsId: String,
  val bookProgress: Double,
  val bookTitle: String,
  val deviceID: String,
  val time: DateTime,
  val uri: URI?,
) {

  private val dateFormatter =
    ISODateTimeFormat.dateTime()
      .withZoneUTC()

  /**
   * Add the fields of this object to the given message digest
   */

  fun addToDigest(
    digest: MessageDigest
  ) {
    BookmarkDigests.addToDigest(digest, this.bookChapterProgress)
    BookmarkDigests.addToDigest(digest, this.bookChapterTitle)
    BookmarkDigests.addToDigest(digest, this.bookOpdsId)
    BookmarkDigests.addToDigest(digest, this.bookProgress)
    BookmarkDigests.addToDigest(digest, this.bookTitle)
    BookmarkDigests.addToDigest(digest, this.deviceID)
    BookmarkDigests.addToDigest(digest, this.dateFormatter.print(this.time))
    this.uri.let { x -> BookmarkDigests.addToDigest(digest, x.toString()) }
  }
}
