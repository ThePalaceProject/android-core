package org.nypl.simplified.books.api.bookmark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.books.api.BookLocation
import org.nypl.simplified.books.api.helper.ReaderLocationJSON
import org.nypl.simplified.books.api.helper.AudiobookLocationJSON
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONSerializerUtilities
import org.nypl.simplified.opds.core.getOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Functions to serialize bookmarks to/from JSON.
 */

object BookmarkJSON {

  private val dateFormatter =
    ISODateTimeFormat.dateTime()
      .withZoneUTC()

  private val dateParserWithTimezone =
    ISODateTimeFormat.dateTimeParser()
      .withOffsetParsed()

  private val dateParserWithUTC =
    ISODateTimeFormat.dateTimeParser()
      .withZoneUTC()

  /**
   * Deserialize bookmarks from the given JSON node.
   *
   * @param kind The kind of bookmark
   * @param node A JSON node
   * @return A reader bookmark
   * @throws JSONParseException On parse errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun deserializeReaderBookmarkFromJSON(
    kind: BookmarkKind,
    node: JsonNode
  ): Bookmark.ReaderBookmark {
    return deserializeReaderBookmarkFromJSON(
      kind = kind,
      node = JSONParserUtilities.checkObject(null, node)
    )
  }

  /**
   * Deserialize bookmarks from the given JSON node.
   *
   * @param kind The kind of bookmark
   * @param node A JSON node
   * @return A reader bookmark
   * @throws JSONParseException On parse errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun deserializeReaderBookmarkFromJSON(
    kind: BookmarkKind,
    node: ObjectNode
  ): Bookmark.ReaderBookmark {
    return when (val version = JSONParserUtilities.getIntegerOrNull(node, "@version")) {
      20210828 ->
        deserializeReaderBookmarkFromJSON20210828(kind, node)
      20210317 ->
        deserializeReaderBookmarkFromJSON20210828(kind, node)
      null ->
        deserializeReaderBookmarkFromJSONOld(kind, node)
      else ->
        throw JSONParseException("Unsupported bookmark version: $version")
    }
  }

  /**
   * Deserialize bookmarks from the given JSON node.
   *
   * @param objectMapper A JSON object mapper
   * @param kind The kind of bookmark
   * @param node A JSON node
   * @return An audiobook bookmark
   * @throws JSONParseException On parse errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun deserializeFromJSON(
    objectMapper: ObjectMapper,
    kind: BookmarkKind,
    node: JsonNode
  ): Bookmark.AudiobookBookmark {
    return deserializeFromJSON(
      objectMapper = objectMapper,
      kind = kind,
      node = JSONParserUtilities.checkObject(null, node)
    )
  }

  /**
   * Deserialize bookmarks from the given JSON node.
   *
   * @param kind The kind of bookmark
   * @param node A JSON node
   * @return An audiobook bookmark
   * @throws JSONParseException On parse errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun deserializeAudiobookBookmarkFromJSON(
    kind: BookmarkKind,
    node: ObjectNode
  ): Bookmark.AudiobookBookmark {
    val locationJSON = JSONParserUtilities.getObjectOrNull(node, "location")
    val location =
      AudiobookLocationJSON.deserializeFromJSON(locationJSON)

    val duration = JSONParserUtilities.getIntegerDefault(locationJSON, "duration", 0).toLong()

    val timeParsed = parseTime(JSONParserUtilities.getString(node, "time"))

    return Bookmark.AudiobookBookmark.create(
      opdsId = JSONParserUtilities.getString(node, "opdsId"),
      kind = kind,
      location = location,
      duration = duration,
      time = timeParsed,
      uri = toNullable(JSONParserUtilities.getURIOptional(node, "uri")),
      deviceID = JSONParserUtilities.getStringDefault(node, "deviceID", null)
    )
  }

  private fun deserializeReaderBookmarkFromJSON20210828(
    kind: BookmarkKind,
    node: ObjectNode
  ): Bookmark.ReaderBookmark {
    val location =
      ReaderLocationJSON.deserializeFromJSON(
        JSONParserUtilities.getObject(node, "location")
      )

    val timeParsed =
      parseTime(JSONParserUtilities.getString(node, "time"))

    return Bookmark.ReaderBookmark.create(
      opdsId = JSONParserUtilities.getString(node, "opdsId"),
      kind = kind,
      location = location,
      time = timeParsed,
      chapterTitle = JSONParserUtilities.getString(node, "chapterTitle"),
      bookProgress = JSONParserUtilities.getDoubleDefault(node, "bookProgress", 0.0),
      uri = toNullable(JSONParserUtilities.getURIOptional(node, "uri")),
      deviceID = JSONParserUtilities.getStringDefault(node, "deviceID", null)
    )
  }

  private fun deserializeReaderBookmarkFromJSONOld(
    kind: BookmarkKind,
    node: ObjectNode
  ): Bookmark.ReaderBookmark {
    val location =
      ReaderLocationJSON.deserializeFromJSON(
        JSONParserUtilities.getObject(node, "location")
      )

    /*
     * Old bookmarks have a top-level chapterProgress value. We've moved to having this
     * stored explicitly in book locations for modern bookmarks. We pick whichever is
     * the greater of the two possible values, because we default to 0.0 for missing
     * values.
     */

    val chapterProgress =
      JSONParserUtilities.getDoubleDefault(node, "chapterProgress", 0.0)

    val locationMax =
      when (location) {
        is BookLocation.BookLocationR2 ->
          location
        is BookLocation.BookLocationR1 ->
          location.copy(progress = Math.max(location.progress ?: 0.0, chapterProgress))
      }

    return Bookmark.ReaderBookmark(
      opdsId = JSONParserUtilities.getString(node, "opdsId"),
      kind = kind,
      location = locationMax,
      time = parseTime(JSONParserUtilities.getString(node, "time")),
      chapterTitle = JSONParserUtilities.getString(node, "chapterTitle"),
      bookProgress = JSONParserUtilities.getDoubleOptional(node, "bookProgress").getOrNull(),
      uri = toNullable(JSONParserUtilities.getURIOptional(node, "uri")),
      deviceID = JSONParserUtilities.getStringDefault(node, "deviceID", null)
    )
  }

  /**
   * Correctly parse a date/time value.
   *
   * This slightly odd function first attempts to parse the incoming string as if it was
   * a date/time string with an included time zone. If the time string turned out not to
   * include a time zone, Joda Time will parse it using the system's default timezone. We
   * then detect that this has happened and, if the current system's timezone isn't UTC,
   * we parse the string *again* but this time assuming a UTC timezone.
   */

  private fun parseTime(
    timeText: String
  ): DateTime {
    val defaultZone = DateTimeZone.getDefault()
    val timeParsedWithZone = dateParserWithTimezone.parseDateTime(timeText)
    if (timeParsedWithZone.zone == defaultZone && defaultZone != DateTimeZone.UTC) {
      return dateParserWithUTC.parseDateTime(timeText)
    }
    return timeParsedWithZone.toDateTime(DateTimeZone.UTC)
  }

  private fun <T> toNullable(option: OptionType<T>): T? {
    return if (option is Some<T>) {
      option.get()
    } else {
      null
    }
  }

  /**
   * Serialize a bookmark to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmark A reader bookmark
   * @return A serialized object
   */

  @JvmStatic
  fun serializeReaderBookmarkToJSON(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.ReaderBookmark
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put("@version", 20210828)
    node.put("opdsId", bookmark.opdsId)
    val location = ReaderLocationJSON.serializeToJSON(objectMapper, bookmark.location)
    node.set<ObjectNode>("location", location)
    node.put("time", dateFormatter.print(bookmark.time))
    node.put("chapterTitle", bookmark.chapterTitle)
    bookmark.bookProgress?.let { node.put("bookProgress", it) }
    bookmark.deviceID.let { device -> node.put("deviceID", device) }
    return node
  }

  /**
   * Serialize a bookmark to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmarks A list of reader bookmarks
   * @return A serialized object
   */

  @JvmStatic
  fun serializeReaderBookmarksToJSON(
    objectMapper: ObjectMapper,
    bookmarks: List<Bookmark.ReaderBookmark>
  ): ArrayNode {
    val node = objectMapper.createArrayNode()
    bookmarks.forEach { bookmark ->
      node.add(
        serializeReaderBookmarkToJSON(
          objectMapper,
          bookmark
        )
      )
    }
    return node
  }

  /**
   * Serialize a bookmark to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmark A reader bookmark
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serializeReaderBookmarkToString(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.ReaderBookmark
  ): String {
    val json = serializeReaderBookmarkToJSON(objectMapper, bookmark)
    val output = ByteArrayOutputStream(1024)
    JSONSerializerUtilities.serialize(json, output)
    return output.toString("UTF-8")
  }

  /**
   * Serialize a bookmark to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmarks A list of reader bookmarks
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serializeReaderBookmarksToString(
    objectMapper: ObjectMapper,
    bookmarks: List<Bookmark.ReaderBookmark>
  ): String {
    val json = serializeReaderBookmarksToJSON(objectMapper, bookmarks)
    val output = ByteArrayOutputStream(1024)
    val writer = objectMapper.writerWithDefaultPrettyPrinter()
    writer.writeValue(output, json)
    return output.toString("UTF-8")
  }

  /**
   * Deserialize a bookmark from the given string.
   *
   * @param objectMapper A JSON object mapper
   * @param kind The kind of bookmark
   * @param serialized A serialized JSON string
   * @return An audiobook bookmark
   * @throws IOException On I/O or parser errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun deserializeReaderBookmarkFromString(
    objectMapper: ObjectMapper,
    kind: BookmarkKind,
    serialized: String
  ): Bookmark.ReaderBookmark {
    return deserializeReaderBookmarkFromJSON(
      kind = kind,
      node = objectMapper.readTree(serialized)
    )
  }

  /**
   * Serialize a bookmark to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmark An audiobook bookmark
   * @return A serialized object
   */

  @JvmStatic
  fun serializeAudiobookBookmarkToJSON(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.AudiobookBookmark
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put("opdsId", bookmark.opdsId)
    val location = AudiobookLocationJSON.serializeToJSON(objectMapper, bookmark.location)
    node.set<ObjectNode>("location", location)
    node.put("time", dateFormatter.print(bookmark.time))
    node.put("chapterTitle", bookmark.location.title.orEmpty())
    node.put("deviceId", bookmark.deviceID)
    bookmark.deviceID.let { device -> node.put("deviceID", device) }
    return node
  }

  /**
   * Serialize a bookmark to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmarks A list of audiobook bookmarks object mapper
   * @return A serialized object
   */

  @JvmStatic
  fun serializeAudiobookBookmarksToJSON(
    objectMapper: ObjectMapper,
    bookmarks: List<Bookmark.AudiobookBookmark>
  ): ArrayNode {
    val node = objectMapper.createArrayNode()
    bookmarks.forEach { bookmark ->
      node.add(
        serializeAudiobookBookmarkToJSON(
          objectMapper,
          bookmark
        )
      )
    }
    return node
  }

  /**
   * Serialize a bookmark to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmark An audiobook bookmark
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serializeAudiobookBookmarkToString(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.AudiobookBookmark
  ): String {
    val json = serializeAudiobookBookmarkToJSON(objectMapper, bookmark)
    val output = ByteArrayOutputStream(1024)
    JSONSerializerUtilities.serialize(json, output)
    return output.toString("UTF-8")
  }

  /**
   * Serialize a bookmark to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @param bookmarks A list of audiobook bookmarks
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun serializeAudiobookBookmarksToString(
    objectMapper: ObjectMapper,
    bookmarks: List<Bookmark.AudiobookBookmark>
  ): String {
    val json = serializeAudiobookBookmarksToJSON(objectMapper, bookmarks)
    val output = ByteArrayOutputStream(1024)
    val writer = objectMapper.writerWithDefaultPrettyPrinter()
    writer.writeValue(output, json)
    return output.toString("UTF-8")
  }

  /**
   * Deserialize a bookmark from the given string.
   *
   * @param objectMapper A JSON object mapper
   * @param kind The kind of bookmark
   * @param serialized A serialized JSON string
   * @return A parsed location
   * @throws IOException On I/O or parser errors
   */

  @JvmStatic
  @Throws(IOException::class)
  fun deserializeAudiobookBookmarkFromString(
    objectMapper: ObjectMapper,
    kind: BookmarkKind,
    serialized: String
  ): Bookmark.AudiobookBookmark {
    return deserializeAudiobookBookmarkFromJSON(
      kind = kind,
      node = objectMapper.readTree(serialized)
    )
  }

  /**
   * Deserialize bookmarks from the given JSON node.
   *
   * @param kind The kind of bookmark
   * @param node A JSON node
   * @return A parsed description
   * @throws JSONParseException On parse errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun deserializeAudiobookBookmarkFromJSON(
    kind: BookmarkKind,
    node: JsonNode
  ): Bookmark.AudiobookBookmark {
    return deserializeAudiobookBookmarkFromJSON(
      kind = kind,
      node = JSONParserUtilities.checkObject(null, node)
    )
  }
}
