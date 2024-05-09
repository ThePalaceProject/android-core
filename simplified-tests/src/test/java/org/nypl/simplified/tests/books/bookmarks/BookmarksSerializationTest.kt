package org.nypl.simplified.tests.books.bookmarks

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark20210317
import org.nypl.simplified.books.api.bookmark.SerializedBookmark20210828
import org.nypl.simplified.books.api.bookmark.SerializedBookmark20240424
import org.nypl.simplified.books.api.bookmark.SerializedBookmarkFallbackValues
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime1
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime2
import org.nypl.simplified.books.api.bookmark.SerializedLocatorHrefProgression20210317
import org.nypl.simplified.books.api.bookmark.SerializedLocatorLegacyCFI
import org.nypl.simplified.books.api.bookmark.SerializedLocatorPage1
import org.nypl.simplified.books.api.bookmark.SerializedLocators
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.charset.StandardCharsets

class BookmarksSerializationTest {

  private val objectMapper =
    ObjectMapper()
  private val logger =
    LoggerFactory.getLogger(BookmarksSerializationTest::class.java)

  @Test
  fun testFormat20210317() {
    val bookmarkOut =
      SerializedBookmark20210317(
        bookChapterProgress = 0.25,
        bookChapterTitle = "Chapter Title",
        bookProgress = 0.1,
        bookTitle = "Book Title",
        deviceID = "7a68e229-abbd-4bec-99e2-44c23be152d5",
        kind = BookmarkKind.BookmarkExplicit,
        location = SerializedLocatorLegacyCFI(
          idRef = "idRef",
          contentCFI = "/content/cfi",
          chapterProgression = 0.3
        ),
        opdsId = "opdsId",
        time = DateTime.parse("2001-01-01T00:00:00+00:00"),
        uri = URI.create("https://www.example.com/")
      )

    /*
     * The 20210317 format doesn't support many of the fields we use. The parser will return
     * default values.
     */

    val bookmarkLimited =
      bookmarkOut.copy(
        bookChapterProgress = 0.0,
        bookChapterTitle = "",
        bookProgress = 0.0,
        bookTitle = "",
      )

    val bookmarkText =
      bookmarkOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", bookmarkText)

    val bookmarkIn =
      SerializedBookmarks.parseBookmarkFromString(
        text = bookmarkText,
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      ) as SerializedBookmark20210317

    assertEquals(bookmarkLimited, bookmarkIn)
  }

  @Test
  fun testFormat20210828() {
    val bookmarkOut =
      SerializedBookmark20210828(
        bookChapterProgress = 0.25,
        bookChapterTitle = "Chapter Title",
        bookProgress = 0.1,
        bookTitle = "Book Title",
        deviceID = "7a68e229-abbd-4bec-99e2-44c23be152d5",
        kind = BookmarkKind.BookmarkExplicit,
        location = SerializedLocatorLegacyCFI(
          idRef = "idRef",
          contentCFI = "/content/cfi",
          chapterProgression = 0.3
        ),
        opdsId = "opdsId",
        time = DateTime.parse("2001-01-01T00:00:00+00:00"),
        uri = URI.create("https://www.example.com/")
      )

    /*
     * The 20210828 format doesn't support many of the fields we use. The parser will return
     * default values.
     */

    val bookmarkLimited =
      bookmarkOut.copy(
        bookChapterProgress = 0.0,
        bookChapterTitle = "",
        bookProgress = 0.0,
        bookTitle = "",
      )

    val bookmarkText =
      bookmarkOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", bookmarkText)

    val bookmarkIn =
      SerializedBookmarks.parseBookmarkFromString(
        text = bookmarkText,
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      ) as SerializedBookmark20210828

    assertEquals(bookmarkLimited, bookmarkIn)
  }

  @Test
  fun testFormat20240424() {
    val bookmarkOut =
      SerializedBookmark20240424(
        bookChapterProgress = 0.25,
        bookChapterTitle = "Chapter Title",
        bookProgress = 0.1,
        bookTitle = "Book Title",
        deviceID = "7a68e229-abbd-4bec-99e2-44c23be152d5",
        kind = BookmarkKind.BookmarkExplicit,
        location = SerializedLocatorLegacyCFI(
          idRef = "idRef",
          contentCFI = "/content/cfi",
          chapterProgression = 0.3
        ),
        opdsId = "opdsId",
        time = DateTime.parse("2001-01-01T00:00:00+00:00"),
        uri = URI.create("https://www.example.com/")
      )

    val bookmarkLimited =
      bookmarkOut.copy()

    val bookmarkText =
      bookmarkOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", bookmarkText)

    val bookmarkIn =
      SerializedBookmarks.parseBookmarkFromString(
        text = bookmarkText,
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      ) as SerializedBookmark20240424

    assertEquals(bookmarkLimited, bookmarkIn)
  }

  @Test
  fun testLocatorAudioBookTime1() {
    val locatorOut =
      SerializedLocatorAudioBookTime1(
        audioBookId = "x",
        chapter = 1,
        duration = 8640000L,
        part = 2,
        startOffsetMilliseconds = 1000L,
        timeMilliseconds = 3000L,
        title = "Title",
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorAudioBookTime1

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorAudioBookTime2() {
    val locatorOut =
      SerializedLocatorAudioBookTime2(
        readingOrderItem = "urn:org.thepalaceproject:readingOrderItem:23",
        readingOrderItemOffsetMilliseconds = 25000L
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorAudioBookTime2

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorLegacyCFI0() {
    val locatorOut =
      SerializedLocatorLegacyCFI(
        idRef = "/a",
        contentCFI = "/b",
        chapterProgression = 0.5
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorLegacyCFI

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorLegacyCFI1() {
    val locatorOut =
      SerializedLocatorLegacyCFI(
        idRef = "/a",
        contentCFI = null,
        chapterProgression = 0.5
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorLegacyCFI

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorLegacyCFI2() {
    val locatorOut =
      SerializedLocatorLegacyCFI(
        idRef = null,
        contentCFI = "/b",
        chapterProgression = 0.5
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorLegacyCFI

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorLegacyCFI3() {
    val locatorOut =
      SerializedLocatorLegacyCFI(
        idRef = null,
        contentCFI = null,
        chapterProgression = 0.5
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorLegacyCFI

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorPage1() {
    val locatorOut =
      SerializedLocatorPage1(
        page = 23
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorPage1

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testLocatorHrefProgression20210317() {
    val locatorOut =
      SerializedLocatorHrefProgression20210317(
        chapterHref = "/x/y/z",
        chapterProgress = 0.5
      )

    val locatorText =
      locatorOut.toJSON(this.objectMapper).toPrettyString()

    this.logger.debug("{}", locatorText)

    val locatorIn =
      SerializedLocators.parseLocatorFromString(locatorText)
        as SerializedLocatorHrefProgression20210317

    assertEquals(locatorOut, locatorIn)
  }

  @Test
  fun testParseLegacyBookmarks0() {
    val bookmark =
      SerializedBookmarks.parseBookmarkFromString(
        textOf("bookmark-20210317-r1-0.json"),
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      )

    assertEquals("urn:isbn:9781683607144", bookmark.opdsId)
  }

  @Test
  fun testParseLegacyBookmarks1() {
    val bookmark =
      SerializedBookmarks.parseBookmarkFromString(
        textOf("bookmark-20210317-r2-0.json"),
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      )

    assertEquals("urn:isbn:9781683607144", bookmark.opdsId)
  }

  @Test
  fun testParseLegacyBookmarks2() {
    val bookmark =
      SerializedBookmarks.parseBookmarkFromString(
        textOf("bookmark-20210317-r2-1.json"),
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      )

    assertEquals("urn:uuid:808b2d99-c286-499a-91a1-580afc4c563f", bookmark.opdsId)
  }

  @Test
  fun testParseLegacyBookmarks3() {
    val bookmark =
      SerializedBookmarks.parseBookmarkFromString(
        textOf("bookmark-legacy-r1-0.json"),
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      )

    assertEquals("urn:isbn:9781683607144", bookmark.opdsId)
  }

  @Test
  fun testParseLegacyBookmarks4() {
    val bookmark =
      SerializedBookmarks.parseBookmarkFromString(
        textOf("bookmark-legacy-r1-1.json"),
        fallbackValues = SerializedBookmarkFallbackValues(kind = BookmarkKind.BookmarkLastReadLocation)
      )

    assertEquals("urn:isbn:9781683606123", bookmark.opdsId)
  }

  private fun textOf(
    name: String
  ): String {
    return BookmarksSerializationTest::class.java.getResourceAsStream(
      "/org/nypl/simplified/tests/bookmarks/$name"
    ).readAllBytes()
      .toString(StandardCharsets.UTF_8)
  }
}
