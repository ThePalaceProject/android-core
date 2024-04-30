package org.nypl.simplified.bookmarks.api

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import org.nypl.simplified.books.api.bookmark.SerializedLocators
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities

object BookmarkAnnotationsJSON {

  @Throws(JSONParseException::class)
  fun deserializeSelectorNodeFromJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode
  ): BookmarkAnnotationSelectorNode {
    val type =
      JSONParserUtilities.getString(node, "type")
    val value =
      JSONParserUtilities.getString(node, "value")

    /*
     * Attempt to deserialize the value as a location in order to check the structure. We
     * don't actually need the parsed value here.
     */

    try {
      val selectorNode = objectMapper.readTree(value)
      SerializedLocators.parseLocator(selectorNode)
    } catch (e: Exception) {
      throw JSONParseException(e)
    }

    when (type) {
      "FragmentSelector",
      "oa:FragmentSelector" ->
        Unit
      else -> {
        throw JSONParseException("Unrecognized selector node type: $type")
      }
    }

    return BookmarkAnnotationSelectorNode(
      type = type,
      value = value
    )
  }

  fun serializeSelectorNodeToJSON(
    objectMapper: ObjectMapper,
    selector: BookmarkAnnotationSelectorNode
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put("type", selector.type)
    node.put("value", selector.value)
    return node
  }

  fun serializeTargetNodeToJSON(
    objectMapper: ObjectMapper,
    target: BookmarkAnnotationTargetNode
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put("source", target.source)
    node.set<ObjectNode>(
      "selector",
      serializeSelectorNodeToJSON(objectMapper, target.selector)
    )
    return node
  }

  @Throws(JSONParseException::class)
  fun deserializeTargetNodeFromJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode
  ): BookmarkAnnotationTargetNode {
    return BookmarkAnnotationTargetNode(
      source = JSONParserUtilities.getString(node, "source"),
      selector = deserializeSelectorNodeFromJSON(
        objectMapper,
        JSONParserUtilities.getObject(node, "selector")
      )
    )
  }

  fun serializeBodyNodeToJSON(
    mapper: ObjectMapper,
    target: BookmarkAnnotationBodyNode
  ): ObjectNode {
    val node = mapper.createObjectNode()
    node.put("http://librarysimplified.org/terms/time", target.timestamp)
    node.put("http://librarysimplified.org/terms/device", target.device)
    target.chapterTitle?.let { v ->
      node.put("http://librarysimplified.org/terms/chapter", v)
    }
    target.bookProgress?.let { v ->
      node.put("http://librarysimplified.org/terms/progressWithinBook", v)
    }
    return node
  }

  private fun <T> mapOptionNull(option: OptionType<T>): T? {
    return if (option is Some<T>) {
      option.get()
    } else {
      null
    }
  }

  @Throws(JSONParseException::class)
  fun deserializeBodyNodeFromJSON(node: ObjectNode): BookmarkAnnotationBodyNode {
    return BookmarkAnnotationBodyNode(
      timestamp =
      JSONParserUtilities.getString(node, "http://librarysimplified.org/terms/time"),
      device =
      JSONParserUtilities.getString(node, "http://librarysimplified.org/terms/device"),
      chapterTitle =
      JSONParserUtilities.getStringDefault(
        node,
        "http://librarysimplified.org/terms/chapter",
        ""
      ),
      bookProgress = JSONParserUtilities.getDoubleDefault(
        node,
        "http://librarysimplified.org/terms/progressWithinBook",
        0.0
      ).toFloat()
    )
  }

  fun serializeBookmarkAnnotationToJSON(
    mapper: ObjectMapper,
    annotation: BookmarkAnnotation
  ): ObjectNode {
    val node = mapper.createObjectNode()
    if (annotation.context != null) {
      node.put("@context", annotation.context)
    }
    if (annotation.id != null) {
      node.put("id", annotation.id)
    }
    node.put("motivation", annotation.motivation)
    node.put("type", annotation.type)
    node.set<ObjectNode>("target", serializeTargetNodeToJSON(mapper, annotation.target))
    node.set<ObjectNode>("body", serializeBodyNodeToJSON(mapper, annotation.body))
    return node
  }

  fun serializeBookmarkAnnotationToBytes(
    objectMapper: ObjectMapper,
    annotation: BookmarkAnnotation
  ): ByteArray {
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false)
    objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    return objectMapper.writeValueAsBytes(
      serializeBookmarkAnnotationToJSON(
        objectMapper,
        annotation
      )
    )
  }

  @Throws(JSONParseException::class)
  fun deserializeBookmarkAnnotationFromJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode
  ): BookmarkAnnotation {
    return BookmarkAnnotation(
      context =
      mapOptionNull(JSONParserUtilities.getStringOptional(node, "@context")),
      body =
      deserializeBodyNodeFromJSON(JSONParserUtilities.getObject(node, "body")),
      id =
      mapOptionNull(JSONParserUtilities.getStringOptional(node, "id")),
      type =
      JSONParserUtilities.getString(node, "type"),
      motivation =
      JSONParserUtilities.getString(node, "motivation"),
      target =
      deserializeTargetNodeFromJSON(
        objectMapper,
        JSONParserUtilities.getObject(node, "target")
      )
    )
  }

  fun serializeBookmarkAnnotationFirstNodeToJSON(
    objectMapper: ObjectMapper,
    annotation: BookmarkAnnotationFirstNode
  ): ObjectNode {
    val nodes = objectMapper.createArrayNode()
    annotation.items.forEach { mark ->
      nodes.add(
        serializeBookmarkAnnotationToJSON(
          objectMapper,
          mark
        )
      )
    }

    val node = objectMapper.createObjectNode()
    node.put("id", annotation.id)
    node.put("type", annotation.type)
    node.set<ArrayNode>("items", nodes)
    return node
  }

  @Throws(JSONParseException::class)
  fun deserializeBookmarkAnnotationFirstNodeFromJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode
  ): BookmarkAnnotationFirstNode {
    val bookmarkAnnotations = arrayListOf<BookmarkAnnotation>()
    JSONParserUtilities.getArray(node, "items").map { item ->
      try {
        val bookmarkAnnotation = deserializeBookmarkAnnotationFromJSON(
          objectMapper = objectMapper,
          node = JSONParserUtilities.checkObject(null, item)
        )
        bookmarkAnnotations.add(bookmarkAnnotation)
      } catch (exception: JSONParseException) {
        Log.d("BookmarkAnnotationsJSON", "Error deserializing bookmark annotation")
      }
    }

    return BookmarkAnnotationFirstNode(
      type = JSONParserUtilities.getString(node, "type"),
      id = JSONParserUtilities.getString(node, "id"),
      items = bookmarkAnnotations
    )
  }

  fun serializeBookmarkAnnotationResponseToJSON(
    mapper: ObjectMapper,
    annotation: BookmarkAnnotationResponse
  ): ObjectNode {
    val node = mapper.createObjectNode()
    node.put("total", annotation.total)
    node.put("id", annotation.id)
    node.set<ArrayNode>("@context", serializeStringArray(mapper, annotation.context))
    node.set<ArrayNode>("type", serializeStringArray(mapper, annotation.type))
    node.set<ObjectNode>(
      "first",
      serializeBookmarkAnnotationFirstNodeToJSON(mapper, annotation.first)
    )
    return node
  }

  private fun serializeStringArray(
    objectMapper: ObjectMapper,
    context: List<String>
  ): ArrayNode {
    val array = objectMapper.createArrayNode()
    context.forEach { text -> array.add(text) }
    return array
  }

  @Throws(JSONParseException::class)
  fun deserializeBookmarkAnnotationResponseFromJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode
  ): BookmarkAnnotationResponse {
    return BookmarkAnnotationResponse(
      context =
      JSONParserUtilities.getArray(node, "@context")
        .map { v -> JSONParserUtilities.checkString(v) },
      total =
      JSONParserUtilities.getInteger(node, "total"),
      type =
      JSONParserUtilities.getArray(node, "type")
        .map { v -> JSONParserUtilities.checkString(v) },
      id =
      JSONParserUtilities.getString(node, "id"),
      first =
      deserializeBookmarkAnnotationFirstNodeFromJSON(
        objectMapper = objectMapper,
        node = JSONParserUtilities.getObject(node, "first")
      )
    )
  }

  @Throws(JSONParseException::class)
  fun deserializeBookmarkAnnotationResponseFromJSON(
    objectMapper: ObjectMapper,
    node: JsonNode
  ): BookmarkAnnotationResponse {
    return deserializeBookmarkAnnotationResponseFromJSON(
      objectMapper = objectMapper,
      node = JSONParserUtilities.checkObject(null, node),
    )
  }
}
