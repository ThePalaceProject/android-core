package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.security.MessageDigest

/**
 * @see "https://github.com/ThePalaceProject/mobile-specs/tree/main/bookmarks#locators"
 */

sealed class SerializedLocator {

  /**
   * The type name (such as "BookLocationR2")
   */

  abstract val typeName: String

  /**
   * The type version (such as 20210317)
   */

  abstract val typeVersion: Int

  /**
   * Serialize this locator to JSON.
   */

  abstract fun toJSON(objectMapper: ObjectMapper): ObjectNode

  /**
   * Serialize this locator to a JSON string.
   */

  fun toJSONString(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(toJSON(objectMapper))
  }

  /**
   * Add the fields of this object to the given message digest
   */

  abstract fun addToDigest(digest: MessageDigest)
}
