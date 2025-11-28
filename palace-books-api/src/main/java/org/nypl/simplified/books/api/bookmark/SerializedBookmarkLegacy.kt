package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import java.net.URI
import java.security.MessageDigest

data class SerializedBookmarkLegacy(
  override val bookChapterProgress: Double,
  override val bookChapterTitle: String,
  override val bookTitle: String,
  override val deviceID: String,
  override val kind: BookmarkKind,
  override val location: SerializedLocator,
  override val opdsId: String,
  override val time: DateTime,
  override val uri: URI?,
  override val bookProgress: Double
) : SerializedBookmark() {

  private val dateFormatter =
    ISODateTimeFormat.dateTime()
      .withZoneUTC()

  override val typeName: String
    get() = "Bookmark"

  override val typeVersion: Int
    get() = 20210316

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

    // Legacy bookmarks had these at the top level
    node.put("bookProgress", this.bookProgress)
    node.put("chapterTitle", this.bookChapterTitle)

    when (this.location) {
      is SerializedLocatorAudioBookTime1 -> {
        node.put(
          "chapterProgress",
          this.location.timeMilliseconds.toDouble() / this.location.duration.toDouble()
        )
      }
      is SerializedLocatorHrefProgression20210317 -> {
        node.put("chapterProgress", this.location.chapterProgress)
      }
      is SerializedLocatorLegacyCFI -> {
        node.put("chapterProgress", this.location.chapterProgression)
      }
      is SerializedLocatorPage1 -> {
        // Nothing
      }
      is SerializedLocatorAudioBookTime2 -> {
        // Nothing
      }
    }

    val location = this.location.toJSON(objectMapper)
    node.set<ObjectNode>("location", location)
    node.put("time", dateFormatter.print(this.time))
    node.put("kind", this.kind.toString())
    this.deviceID.let { device -> node.put("deviceID", device) }
    this.uri.let { u -> node.put("uri", u.toString()) }
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
