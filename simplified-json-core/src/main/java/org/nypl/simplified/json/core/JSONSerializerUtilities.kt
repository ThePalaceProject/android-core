package org.nypl.simplified.json.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.io.OutputStream

/**
 * Utilities for implementing JSON serializers.
 */
object JSONSerializerUtilities {

  private val objectMapper: ObjectMapper = ObjectMapper()

  /**
   * Serialize the given object node to the given stream.
   *
   * @param d  The node
   * @param os The output stream
   *
   * @throws IOException On I/O errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serialize(
    d: ObjectNode,
    os: OutputStream
  ) {
    this.objectMapper.writerWithDefaultPrettyPrinter()
      .writeValue(os, d)
  }

  /**
   * Serialize the given object node to a string.
   *
   * @param d The node
   *
   * @return Pretty-printed JSON
   *
   * @throws IOException On I/O errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serializeToString(
    d: ObjectNode
  ): String {
    return this.objectMapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(d)
  }

  /**
   * Serialize the given object node to a string.
   *
   * @param d The node
   *
   * @return Pretty-printed JSON
   *
   * @throws IOException On I/O errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serializeToString(
    d: List<JsonNode>
  ): String {
    val a = this.objectMapper.createArrayNode()
    for (o in d) {
      a.add(o)
    }
    return this.objectMapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(a)
  }
}
