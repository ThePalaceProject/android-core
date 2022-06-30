package org.nypl.simplified.books.api.helper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.books.api.BookChapterProgress
import org.nypl.simplified.books.api.BookLocation
import org.nypl.simplified.books.api.BookLocation.BookLocationR1
import org.nypl.simplified.books.api.BookLocation.BookLocationR2
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONSerializerUtilities
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Functions to serialize and reader book locations to/from JSON.
 */

object ReaderLocationJSON {

  /**
   * Deserialize chapter progress from the given JSON node.
   *
   * @param node A JSON node
   * @return A parsed description
   * @throws JSONParseException On parse errors
   */

  @Throws(JSONParseException::class)
  fun deserializeProgressFromJSON(
    node: JsonNode
  ): BookChapterProgress {
    val obj = JSONParserUtilities.checkObject(null, node)
    return BookChapterProgress(
      chapterHref = JSONParserUtilities.getString(obj, "chapterHref"),
      chapterProgress = JSONParserUtilities.getDouble(obj, "chapterProgress")
    )
  }

  /**
   * Deserialize reader book locations from the given JSON node.
   *
   * @param node A JSON node
   * @return A book location
   * @throws JSONParseException On parse errors
   */

  @Throws(JSONParseException::class)
  fun deserializeFromJSON(
    node: JsonNode
  ): BookLocation {
    val obj =
      JSONParserUtilities.checkObject(null, node)
    val type =
      JSONParserUtilities.getStringOrNull(obj, "@type")
        ?: return deserializeFromJSONR1Old(obj)
    return when (type) {
      "BookLocationR2" ->
        deserializeFromJSONR2(obj)
      "BookLocationR1" ->
        deserializeFromJSONR1(obj)
      else ->
        throw JSONParseException("Unsupported location type: $type")
    }
  }

  private fun deserializeFromJSONR1(
    obj: ObjectNode
  ): BookLocationR1 {
    val version =
      JSONParserUtilities.getIntegerOrNull(obj, "@version")
        ?: return deserializeFromJSONR1Old(obj)
    return when (version) {
      20210317 ->
        deserializeFromJSONR1_20210317(obj)
      else ->
        throw JSONParseException("Unsupported book location format version: $version")
    }
  }

  private fun deserializeFromJSONR1Old(
    obj: ObjectNode
  ): BookLocationR1 {
    return BookLocationR1(
      progress = JSONParserUtilities.getDoubleDefault(obj, "chapterProgress", 0.0),
      contentCFI = JSONParserUtilities.getStringOrNull(obj, "contentCFI"),
      idRef = JSONParserUtilities.getStringOrNull(obj, "idref")
    )
  }

  private fun deserializeFromJSONR1_20210317(
    obj: ObjectNode
  ): BookLocationR1 {
    return BookLocationR1(
      progress = JSONParserUtilities.getDouble(obj, "chapterProgress"),
      contentCFI = JSONParserUtilities.getStringOrNull(obj, "contentCFI"),
      idRef = JSONParserUtilities.getStringOrNull(obj, "idref")
    )
  }

  @Throws(JSONParseException::class)
  private fun deserializeFromJSONR2(
    obj: ObjectNode
  ): BookLocation {
    val version =
      JSONParserUtilities.getIntegerOrNull(obj, "@version")
        ?: return deserializeFromJSONR2Old()
    return when (version) {
      20210317 ->
        deserializeFromJSONR2_20210317(obj)
      else ->
        throw JSONParseException("Unsupported book location format version: $version")
    }
  }

  @Throws(JSONParseException::class)
  private fun deserializeFromJSONR2_20210317(
    obj: ObjectNode
  ): BookLocationR2 {
    val progress = JSONParserUtilities.getObject(obj, "progress")
    return BookLocationR2(deserializeProgressFromJSON(progress))
  }

  @Throws(JSONParseException::class)
  private fun deserializeFromJSONR2Old(): BookLocation {
    throw JSONParseException("Unsupported book location format version: (unspecified)")
  }

  /**
   * Serialize reader book locations to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @return A serialized object
   */

  fun serializeToJSON(
    objectMapper: ObjectMapper,
    description: BookLocation
  ): ObjectNode {
    return when (description) {
      is BookLocationR2 ->
        serializeToJSONR2(objectMapper, description)
      is BookLocationR1 ->
        serializeToJSONR1(objectMapper, description)
    }
  }

  private fun serializeToJSONR2(
    objectMapper: ObjectMapper,
    description: BookLocationR2
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("@type", "BookLocationR2")
    root.put("@version", 20210317)

    val progress = objectMapper.createObjectNode()
    progress.put("chapterHref", description.progress.chapterHref)
    progress.put("chapterProgress", description.progress.chapterProgress)

    root.set<ObjectNode>("progress", progress)
    return root
  }

  private fun serializeToJSONR1(
    objectMapper: ObjectMapper,
    description: BookLocationR1
  ): ObjectNode {
    val root = objectMapper.createObjectNode()

    root.put("@type", "BookLocationR1")
    root.put("@version", 20210317)

    val contentCFI = description.contentCFI
    if (contentCFI != null) {
      root.put("contentCFI", contentCFI)
    }
    val idRef = description.idRef
    if (idRef != null) {
      root.put("idref", idRef)
    }

    root.put("chapterProgress", description.progress ?: 0.0)
    return root
  }

  /**
   * Serialize reader book locations to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @Throws(IOException::class)
  fun serializeToString(
    objectMapper: ObjectMapper,
    description: BookLocation
  ): String {
    val jo = serializeToJSON(objectMapper, description)
    val bao = ByteArrayOutputStream(1024)
    JSONSerializerUtilities.serialize(jo, bao)
    return bao.toString("UTF-8")
  }

  /**
   * Deserialize a reader book location from the given string.
   *
   * @param objectMapper A JSON object mapper
   * @return A parsed location
   * @throws IOException On I/O or parser errors
   */

  @Throws(IOException::class)
  fun deserializeFromString(
    objectMapper: ObjectMapper,
    text: String
  ): BookLocation {
    val node = objectMapper.readTree(text)
    return deserializeFromJSON(
      node = JSONParserUtilities.checkObject(null, node)
    )
  }
}
