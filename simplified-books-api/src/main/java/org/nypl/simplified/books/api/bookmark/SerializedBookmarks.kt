package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import java.net.URI

object SerializedBookmarks {

  private val objectMapper: ObjectMapper =
    ObjectMapper()

  @JvmStatic
  @Throws(JSONParseException::class)
  fun parseBookmarkFromString(
    text: String
  ): SerializedBookmark {
    return this.parseBookmark(this.objectMapper.readTree(text))
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  fun parseBookmark(
    node: JsonNode
  ): SerializedBookmark {
    val type = node["@type"]
    return if (type != null) {
      when (type.asText()) {
        "Bookmark" -> {
          this.parseBookmarkGuess(node)
        }
        else -> {
          this.parseBookmarkGuess(node)
        }
      }
    } else {
      this.parseBookmarkGuess(node)
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmarkGuess(
    node: JsonNode
  ): SerializedBookmark {
    val version = node["@version"]
    return if (version != null) {
      when (version.asText()) {
        "20210317" -> {
          this.parseBookmark20210317(node)
        }
        "20210828" -> {
          this.parseBookmark20210828(node)
        }
        "20240424" -> {
          this.parseBookmark20240424(node)
        }
        else -> {
          this.parseBookmarkLegacy(node)
        }
      }
    } else {
      this.parseBookmarkLegacy(node)
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmark20240424(
    node: JsonNode
  ): SerializedBookmark {
    return when (node) {
      is ObjectNode -> {
        val metadata =
          JSONParserUtilities.checkObject("metadata", node.get("metadata"))
        val opdsId =
          JSONParserUtilities.getString(metadata, "opdsId")
        val time =
          JSONParserUtilities.getTimestamp(metadata, "time")
        val deviceId =
          JSONParserUtilities.getStringDefault(metadata, "deviceID", "null")
        val uri =
          JSONParserUtilities.getURIOrNull(metadata, "uri")
        val bookChapterTitle =
          JSONParserUtilities.getStringDefault(metadata, "bookChapterTitle", "")
        val bookTitle =
          JSONParserUtilities.getStringDefault(metadata, "bookTitle", "")
        val bookProgress =
          JSONParserUtilities.getDoubleDefault(metadata, "bookProgress", 0.0)
        val bookChapterProgress =
          JSONParserUtilities.getDoubleDefault(metadata, "bookChapterProgress", 0.0)

        SerializedBookmark20240424(
          bookChapterProgress = bookChapterProgress,
          bookChapterTitle = bookChapterTitle,
          bookProgress = bookProgress,
          bookTitle = bookTitle,
          deviceID = deviceId,
          kind = BookmarkKind.BookmarkExplicit,
          location = SerializedLocators.parseLocator(node.get("location")),
          opdsId = opdsId,
          time = time,
          uri = uri,
        )
      }
      else -> {
        throw JSONParseException(
          String.format(
            "Bookmarks can only be parsed from JSON object nodes (received %s)",
            node.nodeType
          )
        )
      }
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmarkLegacy(
    node: JsonNode
  ): SerializedBookmarkLegacy {
    return when (node) {
      is ObjectNode -> {
        SerializedBookmarkLegacy(
          bookChapterProgress = 0.0,
          bookChapterTitle = JSONParserUtilities.getStringDefault(node, "chapterTitle", ""),
          bookProgress = JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0),
          bookTitle = "",
          deviceID = JSONParserUtilities.getStringDefault(node, "deviceID", "null"),
          kind = BookmarkKind.BookmarkExplicit,
          location = SerializedLocators.parseLocator(node.get("location")),
          opdsId = JSONParserUtilities.getString(node, "opdsId"),
          time = JSONParserUtilities.getTimestamp(node, "time"),
          uri = JSONParserUtilities.getURIOrNull(node, "uri"),
        )
      }
      else -> {
        throw JSONParseException(
          String.format(
            "Bookmarks can only be parsed from JSON object nodes (received %s)",
            node.nodeType
          )
        )
      }
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmark20210317(
    node: JsonNode
  ): SerializedBookmark20210317 {
    return when (node) {
      is ObjectNode -> {
        SerializedBookmark20210317(
          bookChapterProgress = 0.0,
          bookChapterTitle = JSONParserUtilities.getStringDefault(node, "chapterTitle", ""),
          bookProgress = JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0),
          bookTitle = "",
          deviceID = JSONParserUtilities.getStringDefault(node, "deviceID", "null"),
          kind = BookmarkKind.BookmarkExplicit,
          location = SerializedLocators.parseLocator(node.get("location")),
          opdsId = JSONParserUtilities.getString(node, "opdsId"),
          time = JSONParserUtilities.getTimestamp(node, "time"),
          uri = JSONParserUtilities.getURIOrNull(node, "uri"),
        )
      }
      else -> {
        throw JSONParseException(
          String.format(
            "Bookmarks can only be parsed from JSON object nodes (received %s)",
            node.nodeType
          )
        )
      }
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmark20210828(
    node: JsonNode
  ): SerializedBookmark20210828 {
    return when (node) {
      is ObjectNode -> {
        SerializedBookmark20210828(
          bookChapterProgress = 0.0,
          bookChapterTitle = JSONParserUtilities.getStringDefault(node, "chapterTitle", ""),
          bookProgress = JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0),
          bookTitle = "",
          deviceID = JSONParserUtilities.getStringDefault(node, "deviceID", "null"),
          kind = BookmarkKind.BookmarkExplicit,
          location = SerializedLocators.parseLocator(node.get("location")),
          opdsId = JSONParserUtilities.getString(node, "opdsId"),
          time = JSONParserUtilities.getTimestamp(node, "time"),
          uri = JSONParserUtilities.getURIOrNull(node, "uri"),
        )
      }
      else -> {
        throw JSONParseException(
          String.format(
            "Bookmarks can only be parsed from JSON object nodes (received %s)",
            node.nodeType
          )
        )
      }
    }
  }

  fun createWithCurrentFormat(
    bookChapterProgress: Double,
    bookChapterTitle: String,
    bookProgress: Double,
    bookTitle: String,
    deviceID: String,
    kind: BookmarkKind,
    location: SerializedLocator,
    opdsId: String,
    time: DateTime,
    uri: URI?
  ): SerializedBookmark {
    return SerializedBookmark20240424(
      bookChapterProgress = bookChapterProgress,
      bookChapterTitle = bookChapterTitle,
      bookProgress = bookProgress,
      bookTitle = bookTitle,
      deviceID = deviceID,
      kind = kind,
      location = location,
      opdsId = opdsId,
      time = time,
      uri = uri
    )
  }
}
