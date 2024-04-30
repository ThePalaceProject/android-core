package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import java.math.BigInteger

object SerializedLocators {

  private val objectMapper: ObjectMapper =
    ObjectMapper()

  @JvmStatic
  @Throws(JSONParseException::class)
  fun parseLocatorFromString(
    text: String
  ): SerializedLocator {
    return this.parseLocator(this.objectMapper.readTree(text))
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  fun parseLocator(
    node: JsonNode
  ): SerializedLocator {
    return when (node) {
      is ObjectNode -> {
        this.parseLocator(node)
      }

      else -> {
        throw JSONParseException(
          String.format(
            "Locators can only be parsed from JSON object nodes (received %s)",
            node.nodeType
          )
        )
      }
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  fun parseLocator(
    node: ObjectNode
  ): SerializedLocator {
    val type = node["@type"]
    return if (type != null) {
      when (type.asText()) {
        "BookLocationR1", "LocatorLegacyCFI" -> {
          this.parseLocatorLegacyCFI(node)
        }

        "LocatorAudioBookTime" -> {
          this.parseLocatorAudioBookTime(node)
        }

        "BookLocationR2", "LocatorHrefProgression" -> {
          this.parseLocatorHrefProgression(node)
        }

        "LocatorPage" -> {
          this.parseLocatorPage(node)
        }

        else -> {
          this.parseLocatorGuess(node)
        }
      }
    } else {
      this.parseLocatorGuess(node)
    }
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseLocatorLegacyCFI(
    node: ObjectNode
  ): SerializedLocatorLegacyCFI {
    return SerializedLocatorLegacyCFI(
      idRef = JSONParserUtilities.getStringOrNull(node, "idref"),
      contentCFI = JSONParserUtilities.getStringOrNull(node, "contentCFI"),
      chapterProgression = JSONParserUtilities.getDoubleDefault(node, "progressWithinChapter", 0.0)
    )
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseLocatorGuess(
    node: ObjectNode
  ): SerializedLocator {
    return this.parseLocatorLegacyCFI(node)
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseLocatorPage(
    node: ObjectNode
  ): SerializedLocatorPage1 {
    return SerializedLocatorPage1(
      page = JSONParserUtilities.getInteger(node, "page"),
    )
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseLocatorHrefProgression(
    node: ObjectNode
  ): SerializedLocatorHrefProgression20210317 {
    return SerializedLocatorHrefProgression20210317(
      chapterHref = JSONParserUtilities.getString(node, "href"),
      chapterProgress = JSONParserUtilities.getDouble(node, "progressWithinChapter")
    )
  }

  @JvmStatic
  @Throws(JSONParseException::class)
  private fun parseLocatorAudioBookTime(
    node: ObjectNode
  ): SerializedLocator {
    return when (val version = JSONParserUtilities.getIntegerDefault(node, "@version", 1)) {
      1 -> this.parseLocatorAudioBookTime1(node)
      2 -> this.parseLocatorAudioBookTime2(node)

      else -> {
        throw JSONParseException(
          String.format(
            "Unsupported audio book locator version (received %s)",
            version
          )
        )
      }
    }
  }

  private fun parseLocatorAudioBookTime2(
    node: ObjectNode
  ): SerializedLocatorAudioBookTime2 {
    return SerializedLocatorAudioBookTime2(
      chapterHref =
      JSONParserUtilities.getString(node, "chapterHref"),
      chapterOffsetMilliseconds =
      JSONParserUtilities.getBigInteger(node, "chapterOffsetMilliseconds").toLong()
    )
  }

  private fun parseLocatorAudioBookTime1(
    node: ObjectNode
  ): SerializedLocatorAudioBookTime1 {
    val startOffset =
      JSONParserUtilities.getBigIntegerDefault(node, "startOffset", BigInteger.ZERO)
        .toLong()
    val timeMillisecondsRaw =
      JSONParserUtilities.getBigInteger(node, "time")
        .toLong()

    return SerializedLocatorAudioBookTime1(
      part = JSONParserUtilities.getInteger(node, "part"),
      chapter = JSONParserUtilities.getInteger(node, "chapter"),
      title = JSONParserUtilities.getString(node, "title"),
      audioBookId = JSONParserUtilities.getString(node, "audiobookID"),
      duration = JSONParserUtilities.getBigInteger(node, "duration").toLong(),
      startOffsetMilliseconds = startOffset,
      timeMilliseconds = timeMillisecondsRaw,
    )
  }
}
