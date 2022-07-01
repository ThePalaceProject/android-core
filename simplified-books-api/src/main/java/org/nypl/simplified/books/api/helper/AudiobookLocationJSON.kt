package org.nypl.simplified.books.api.helper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.librarysimplified.audiobook.api.PlayerPosition
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONSerializerUtilities
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Functions to serialize audiobook locations to/from JSON.
 */

object AudiobookLocationJSON {

  /**
   * Deserialize audiobook player positions from the given JSON node.
   *
   * @param node A JSON node
   * @return A parsed player position
   * @throws JSONParseException On parse errors
   */

  @Throws(JSONParseException::class)
  fun deserializeFromJSON(
    node: JsonNode
  ): PlayerPosition {
    val obj =
      JSONParserUtilities.checkObject(null, node)
    return PlayerPosition(
      title = JSONParserUtilities.getStringOrNull(obj, "title"),
      part = JSONParserUtilities.getIntegerDefault(obj, "part", 0),
      chapter = JSONParserUtilities.getIntegerDefault(obj, "chapter", 0),
      offsetMilliseconds =
        (JSONParserUtilities.getDoubleDefault(obj, "time", 0.0) * 1000.0).toLong()
    )
  }

  /**
   * Serialize reader book locations to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @param position The position of the audiobook
   * @return A serialized object
   */

  fun serializeToJSON(
    objectMapper: ObjectMapper,
    position: PlayerPosition
  ): ObjectNode {

    val locationSeconds = position.offsetMilliseconds.toDouble() / 1000.0

    val root = objectMapper.createObjectNode()
    root.put("chapter", position.chapter)
    root.put("time", locationSeconds)
    root.put("part", position.part)
    root.put("title", position.title)
    return root
  }

  /**
   * Serialize reader book locations to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @param position The position in the audiobook
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @Throws(IOException::class)
  fun serializeToString(
    objectMapper: ObjectMapper,
    position: PlayerPosition
  ): String {
    val jo = serializeToJSON(objectMapper, position)
    val bao = ByteArrayOutputStream(1024)
    JSONSerializerUtilities.serialize(jo, bao)
    return bao.toString("UTF-8")
  }

  /**
   * Deserialize a reader book location from the given string.
   *
   * @param objectMapper A JSON object mapper
   * @param text The text to map
   * @return A parsed player position
   * @throws IOException On I/O or parser errors
   */

  @Throws(IOException::class)
  fun deserializeFromString(
    objectMapper: ObjectMapper,
    text: String
  ): PlayerPosition {
    val node = objectMapper.readTree(text)
    return deserializeFromJSON(
      node = JSONParserUtilities.checkObject(null, node)
    )
  }
}
