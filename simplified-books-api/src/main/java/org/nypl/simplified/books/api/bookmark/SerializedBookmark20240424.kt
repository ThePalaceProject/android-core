package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import java.net.URI
import java.security.MessageDigest

data class SerializedBookmark20240424(
  override val bookChapterProgress: Double,
  override val bookChapterTitle: String,
  override val bookProgress: Double,
  override val bookTitle: String,
  override val deviceID: String,
  override val kind: BookmarkKind,
  override val location: SerializedLocator,
  override val opdsId: String,
  override val time: DateTime,
  override val uri: URI?,
) : SerializedBookmark() {

  private val dateFormatter =
    ISODateTimeFormat.dateTime()
      .withZoneUTC()

  override val typeName: String
    get() = "Bookmark"

  override val typeVersion: Int
    get() = 20240424

  override val book: BookID
    get() = BookIDs.newFromText(this.opdsId)

  override val bookmarkId: BookmarkID
    get() = this.createBookmarkId()

  private fun createBookmarkId(): BookmarkID {
    val digest = MessageDigest.getInstance("SHA-256")
    this.addToDigest(digest)

    val digestResult = digest.digest()
    val builder = StringBuilder(64)
    for (index in digestResult.indices) {
      val bb = digestResult[index]
      builder.append(String.format("%02x", bb))
    }
    return BookmarkID(builder.toString())
  }

  override fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put("@type", this.typeName)
    node.put("@version", this.typeVersion)

    val location = this.location.toJSON(objectMapper)
    node.set<ObjectNode>("location", location)

    val metadata = objectMapper.createObjectNode()
    metadata.put("bookChapterProgress", this.bookChapterProgress)
    metadata.put("bookChapterTitle", this.bookChapterTitle)
    metadata.put("bookProgress", this.bookProgress)
    metadata.put("bookTitle", this.bookTitle)
    metadata.put("deviceID", this.deviceID)
    metadata.put("kind", this.kind.motivationURI)
    metadata.put("opdsId", this.opdsId)
    metadata.put("time", this.dateFormatter.print(this.time))
    this.uri.let { x -> metadata.put("uri", x.toString()) }

    node.set<ObjectNode>("metadata", metadata)
    return node
  }

  override fun addToDigest(
    digest: MessageDigest
  ) {
    BookmarkDigests.addToDigest(digest, this.opdsId)
    this.location.addToDigest(digest)
  }

  override fun withURI(uri: URI): SerializedBookmark {
    return this.copy(uri = uri)
  }

  override fun withoutURI(): SerializedBookmark {
    return this.copy(uri = null)
  }
}
