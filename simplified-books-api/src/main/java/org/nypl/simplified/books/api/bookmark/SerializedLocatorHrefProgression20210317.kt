package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/**
 * The 20210317 standardization of R2 "href/progression" locators.
 *
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/bookmarks#locatorhrefprogression"
 */

data class SerializedLocatorHrefProgression20210317(
  /**
   * The href of the chapter.
   */

  val chapterHref: String,

  /**
   * The progress through the chapter.
   */

  val chapterProgress: Double,
) : SerializedLocator() {

  override val typeName: String
    get() = "LocatorHrefProgression"

  override val typeVersion: Int
    get() = 20210317

  override fun toJSON(
    objectMapper: ObjectMapper
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("@type", this.typeName)
    root.put("@version", this.typeVersion)
    root.put("href", this.chapterHref)
    root.put("progressWithinChapter", this.chapterProgress)
    return root
  }

  override fun addToDigest(
    digest: MessageDigest
  ) {
    digest.update(this.chapterHref.toByteArray(UTF_8))
    digest.update(String.format("%.6f", this.chapterProgress).toByteArray(UTF_8))
  }
}
