package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/**
 * The version 1 standardization of "audio book time" locators.
 *
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/bookmarks#locatoraudiobooktime"
 */

data class SerializedLocatorAudioBookTime1(
  val audioBookId: String,
  val chapter: Int,
  val duration: Long,
  val part: Int,
  val startOffsetMilliseconds: Long,
  val timeMilliseconds: Long,
  val title: String,
) : SerializedLocator() {

  init {
    check(this.chapter >= 0) {
      "Chapter ${this.chapter} must be non-negative."
    }
    check(this.part >= 0) {
      "Chapter ${this.part} must be non-negative."
    }
  }

  val timeWithoutOffset: Long
    get() = this.timeMilliseconds - this.startOffsetMilliseconds

  override val typeName: String
    get() = "LocatorAudioBookTime"

  override val typeVersion: Int
    get() = 1

  override fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("@type", this.typeName)
    root.put("@version", this.typeVersion)

    root.put("audiobookID", this.audioBookId)
    root.put("chapter", this.chapter)
    root.put("duration", this.duration)
    root.put("part", this.part)
    root.put("startOffset", this.startOffsetMilliseconds)
    root.put("time", this.timeMilliseconds)
    root.put("title", this.title)
    return root
  }

  override fun addToDigest(
    digest: MessageDigest
  ) {
    digest.update(this.audioBookId.toByteArray(UTF_8))
    digest.update(this.chapter.toString().toByteArray(UTF_8))
    digest.update(this.duration.toString().toByteArray(UTF_8))
    digest.update(this.part.toString().toByteArray(UTF_8))
    digest.update(this.startOffsetMilliseconds.toString().toByteArray(UTF_8))
    digest.update(this.timeMilliseconds.toString().toByteArray(UTF_8))
    digest.update(this.title.toByteArray(UTF_8))
  }
}
