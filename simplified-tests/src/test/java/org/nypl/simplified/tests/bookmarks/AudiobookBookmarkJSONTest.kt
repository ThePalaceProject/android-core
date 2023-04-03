package org.nypl.simplified.tests.bookmarks

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.books.api.bookmark.BookmarkJSON
import org.nypl.simplified.books.api.bookmark.BookmarkKind

class AudiobookBookmarkJSONTest {

  private lateinit var objectMapper: ObjectMapper
  private lateinit var formatter: DateTimeFormatter

  @BeforeEach
  fun testSetup() {
    this.objectMapper = ObjectMapper()
    this.formatter = ISODateTimeFormat.dateTime().withZoneUTC()
  }

  @AfterEach
  fun tearDown() {
    DateTimeZone.setDefault(DateTimeZone.getDefault())
  }

  @Test
  fun testDeserializeJSON() {
    val bookmark = BookmarkJSON.deserializeAudiobookBookmarkFromString(
      objectMapper = this.objectMapper,
      kind = BookmarkKind.BookmarkLastReadLocation,
      serialized = """
        {
          "@version" : 2,
          "opdsId" : "urn:isbn:9781683609438",
          "location" : {
            "chapter" : 1,
            "part" : 2,
            "title" : "Is That You, Walt Whitman?",
            "time" : 100000
          },
          "time" : "2022-06-27T14:51:46.238",
          "chapterTitle" : "Is That You, Walt Whitman?",
          "deviceID" : "null"
        }
      """
    )

    assertEquals(100000, bookmark.location.startOffset)
    assertEquals(1, bookmark.location.chapter)
    assertEquals(2, bookmark.location.part)
    assertEquals("Is That You, Walt Whitman?", bookmark.location.title)

    val serializedText =
      BookmarkJSON.serializeAudiobookBookmarkToString(bookmark)
    val serialized =
      BookmarkJSON.deserializeAudiobookBookmarkFromString(
        objectMapper = this.objectMapper,
        kind = bookmark.kind,
        serialized = serializedText
      )
    assertEquals(bookmark, serialized)
  }

  @Test
  fun testDeserializeJSONV3() {
    val bookmark = BookmarkJSON.deserializeAudiobookBookmarkFromString(
      objectMapper = this.objectMapper,
      kind = BookmarkKind.BookmarkLastReadLocation,
      serialized = """
        {
          "@version" : 3,
          "opdsId" : "urn:isbn:9781683609438",
          "location" : {
            "chapter" : 1,
            "part" : 2,
            "title" : "Is That You, Walt Whitman?",
            "startOffset" : 28000,
            "time" : 100000
          },
          "time" : "2022-06-27T14:51:46.238",
          "chapterTitle" : "Is That You, Walt Whitman?",
          "deviceID" : "null"
        }
      """
    )

    assertEquals(28000, bookmark.location.startOffset)
    assertEquals(100000, bookmark.location.currentOffset)
    assertEquals(1, bookmark.location.chapter)
    assertEquals(2, bookmark.location.part)
    assertEquals("Is That You, Walt Whitman?", bookmark.location.title)

    val serializedText =
      BookmarkJSON.serializeAudiobookBookmarkToString(bookmark)
    val serialized =
      BookmarkJSON.deserializeAudiobookBookmarkFromString(
        objectMapper = this.objectMapper,
        kind = bookmark.kind,
        serialized = serializedText
      )
    assertEquals(bookmark, serialized)
  }
}
