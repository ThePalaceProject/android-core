package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/**
 * The version 1 standardization of "page" locators.
 *
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/bookmarks#locatorpage"
 */

data class SerializedLocatorPage1(
  /**
   * The page number.
   */

  val page: Int,
) : SerializedLocator() {

  override val typeName: String
    get() = "LocatorPage"

  override val typeVersion: Int
    get() = 1

  override fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("@type", this.typeName)
    root.put("@version", this.typeVersion)
    root.put("page", this.page)
    return root
  }

  override fun addToDigest(
    digest: MessageDigest
  ) {
    digest.update(this.page.toString().toByteArray(UTF_8))
  }
}
