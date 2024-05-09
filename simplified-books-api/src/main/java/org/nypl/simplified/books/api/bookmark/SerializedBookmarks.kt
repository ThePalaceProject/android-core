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
    text: String,
    fallbackValues: SerializedBookmarkFallbackValues
  ): SerializedBookmark {
    return this.parseBookmark(
      node = this.objectMapper.readTree(text),
      fallbackValues = fallbackValues
    )
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  fun parseBookmark(
    node: JsonNode,
    fallbackValues: SerializedBookmarkFallbackValues
  ): SerializedBookmark {
    val type = node["@type"]
    return if (type != null) {
      when (type.asText()) {
        "Bookmark" -> {
          this.parseBookmarkGuess(node, fallbackValues)
        }
        else -> {
          this.parseBookmarkGuess(node, fallbackValues)
        }
      }
    } else {
      this.parseBookmarkGuess(node, fallbackValues)
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmarkGuess(
    node: JsonNode,
    fallbackValues: SerializedBookmarkFallbackValues
  ): SerializedBookmark {
    val version = node["@version"]
    return if (version != null) {
      when (version.asText()) {
        "20210317" -> {
          this.parseBookmark20210317(node, fallbackValues)
        }
        "20210828" -> {
          this.parseBookmark20210828(node, fallbackValues)
        }
        "20240424" -> {
          this.parseBookmark20240424(node, fallbackValues)
        }
        else -> {
          this.parseBookmarkLegacy(node, fallbackValues)
        }
      }
    } else {
      this.parseBookmarkLegacy(node, fallbackValues)
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmark20240424(
    node: JsonNode,
    fallbackValues: SerializedBookmarkFallbackValues
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
        val kind =
          this.parseKindMotivation(
            JSONParserUtilities.getStringDefault(
              metadata, "kind", fallbackValues.kind.motivationURI
            ))

        SerializedBookmark20240424(
          bookChapterProgress = bookChapterProgress,
          bookChapterTitle = bookChapterTitle,
          bookProgress = bookProgress,
          bookTitle = bookTitle,
          deviceID = deviceId,
          kind = kind,
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
    node: JsonNode,
    fallbackValues: SerializedBookmarkFallbackValues
  ): SerializedBookmarkLegacy {
    return when (node) {
      is ObjectNode -> {
        val bookChapterTitle =
          JSONParserUtilities.getStringDefault(node, "chapterTitle", "")
        val bookProgress =
          JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0)
        val deviceID =
          JSONParserUtilities.getStringDefault(node, "deviceID", "null")
        val opdsId =
          JSONParserUtilities.getString(node, "opdsId")
        val time =
          JSONParserUtilities.getTimestamp(node, "time")
        val uri =
          JSONParserUtilities.getURIOrNull(node, "uri")

        SerializedBookmarkLegacy(
          bookChapterProgress = 0.0,
          bookChapterTitle = bookChapterTitle,
          bookProgress = bookProgress,
          bookTitle = "",
          deviceID = deviceID,
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
  private fun parseBookmark20210317(
    node: JsonNode,
    fallbackValues: SerializedBookmarkFallbackValues
  ): SerializedBookmark20210317 {
    return when (node) {
      is ObjectNode -> {
        val bookChapterTitle =
          JSONParserUtilities.getStringDefault(node, "chapterTitle", "")
        val bookProgress =
          JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0)
        val deviceID =
          JSONParserUtilities.getStringDefault(node, "deviceID", "null")
        val opdsId =
          JSONParserUtilities.getString(node, "opdsId")
        val time =
          JSONParserUtilities.getTimestamp(node, "time")
        val uri =
          JSONParserUtilities.getURIOrNull(node, "uri")
        val kind =
          this.parseKind20210317(
            JSONParserUtilities.getStringDefault(
              node, "kind", fallbackValues.kind.javaClass.simpleName)
          )

        SerializedBookmark20210317(
          bookChapterProgress = 0.0,
          bookChapterTitle = bookChapterTitle,
          bookProgress = bookProgress,
          bookTitle = "",
          deviceID = deviceID,
          kind = kind,
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

  private fun parseKindMotivation(
    kind: String
  ): BookmarkKind {
    return when (kind) {
      "http://www.w3.org/ns/oa#bookmarking" -> {
        BookmarkKind.BookmarkExplicit
      }
      "http://librarysimplified.org/terms/annotation/idling" -> {
        BookmarkKind.BookmarkLastReadLocation
      }
      else -> {
        throw JSONParseException("Unrecognized bookmark kind: $kind")
      }
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseBookmark20210828(
    node: JsonNode,
    fallbackValues: SerializedBookmarkFallbackValues
  ): SerializedBookmark20210828 {
    return when (node) {
      is ObjectNode -> {
        val bookChapterTitle =
          JSONParserUtilities.getStringDefault(node, "chapterTitle", "")
        val bookProgress =
          JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0)
        val deviceID =
          JSONParserUtilities.getStringDefault(node, "deviceID", "null")
        val kind =
          this.parseKind20210828(
            JSONParserUtilities.getStringDefault(
              node, "kind", fallbackValues.kind.javaClass.simpleName))
        val opdsId =
          JSONParserUtilities.getString(node, "opdsId")
        val time =
          JSONParserUtilities.getTimestamp(node, "time")
        val uri =
          JSONParserUtilities.getURIOrNull(node, "uri")

        SerializedBookmark20210828(
          bookChapterProgress = 0.0,
          bookChapterTitle = bookChapterTitle,
          bookProgress = bookProgress,
          bookTitle = "",
          deviceID = deviceID,
          kind = kind,
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

  private fun parseKind20210828(
    kind: String
  ): BookmarkKind {
    return when (kind) {
      "BookmarkExplicit" -> {
        BookmarkKind.BookmarkExplicit
      }
      "BookmarkLastReadLocation" -> {
        BookmarkKind.BookmarkLastReadLocation
      }
      else -> {
        throw JSONParseException("Unrecognized bookmark kind: $kind")
      }
    }
  }

  private fun parseKind20210317(
    kind: String
  ): BookmarkKind {
    return when (kind) {
      "BookmarkExplicit" -> {
        BookmarkKind.BookmarkExplicit
      }
      "BookmarkLastReadLocation" -> {
        BookmarkKind.BookmarkLastReadLocation
      }
      else -> {
        throw JSONParseException("Unrecognized bookmark kind: $kind")
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
