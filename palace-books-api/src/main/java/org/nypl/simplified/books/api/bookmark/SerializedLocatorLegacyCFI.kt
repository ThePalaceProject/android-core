package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/**
 * The version 1 standardization of legacy "CFI" locators.
 *
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/bookmarks#locatorlegacycfi"
 */

data class SerializedLocatorLegacyCFI(
  val idRef: String?,
  val contentCFI: String?,
  val chapterProgression: Double
) : SerializedLocator() {

  override val typeName: String
    get() = "LocatorLegacyCFI"

  override val typeVersion: Int
    get() = 1

  override fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("@type", this.typeName)
    root.put("@version", this.typeVersion)

    this.idRef?.let { x -> root.put("idref", x) }
    this.contentCFI?.let { x -> root.put("contentCFI", x) }

    root.put("progressWithinChapter", this.chapterProgression)
    return root
  }

  override fun addToDigest(
    digest: MessageDigest
  ) {
    this.idRef?.let { x -> digest.update(x.toByteArray(UTF_8)) }
    this.contentCFI?.let { x -> digest.update(x.toByteArray(UTF_8)) }
    digest.update(String.format("%.6f", this.chapterProgression).toByteArray(UTF_8))
  }
}
