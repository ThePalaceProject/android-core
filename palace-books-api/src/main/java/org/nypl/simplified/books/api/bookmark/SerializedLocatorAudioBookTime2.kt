package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/**
 * The version 2 standardization of "audio book time" locators.
 *
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/bookmarks#locatoraudiobooktime"
 */

data class SerializedLocatorAudioBookTime2(
  val readingOrderItem: String,
  val readingOrderItemOffsetMilliseconds: Long
) : SerializedLocator() {

  override val typeName: String
    get() = "LocatorAudioBookTime"

  override val typeVersion: Int
    get() = 2

  override fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("@type", this.typeName)
    root.put("@version", this.typeVersion)

    root.put("readingOrderItem", this.readingOrderItem)
    root.put("readingOrderItemOffsetMilliseconds", this.readingOrderItemOffsetMilliseconds)
    return root
  }

  override fun addToDigest(
    digest: MessageDigest
  ) {
    digest.update(this.readingOrderItem.toByteArray(UTF_8))
    digest.update(this.readingOrderItemOffsetMilliseconds.toString().toByteArray(UTF_8))
  }
}
