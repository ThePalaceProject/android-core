package org.nypl.simplified.tests.bookmark_annotations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTimeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nypl.simplified.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationBodyNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationFirstNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationResponse
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationSelectorNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationTargetNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationsJSON
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime1
import org.nypl.simplified.books.api.bookmark.SerializedLocatorAudioBookTime2
import org.nypl.simplified.books.api.bookmark.SerializedLocatorHrefProgression20210317
import org.nypl.simplified.books.api.bookmark.SerializedLocatorLegacyCFI
import org.nypl.simplified.books.api.bookmark.SerializedLocatorPage1
import org.nypl.simplified.books.api.bookmark.SerializedLocators
import org.nypl.simplified.json.core.JSONParseException
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.InputStream

class ReaderBookmarkAnnotationsJSONTest {

  private val logger =
    LoggerFactory.getLogger(ReaderBookmarkAnnotationsJSONTest::class.java)

  private val objectMapper: ObjectMapper = ObjectMapper()

  private val targetValue0 =
    "{\n  \"@type\": \"LocatorHrefProgression\",\n  \"href\": \"/0.html\",\n  \"progressWithinChapter\": 0.5\n}\n"
  private val targetValue1 =
    "{\n  \"@type\": \"LocatorHrefProgression\",\n  \"href\": \"/1.html\",\n  \"progressWithinChapter\": 0.5\n}\n"
  private val targetValue2 =
    "{\n  \"@type\": \"LocatorHrefProgression\",\n  \"href\": \"/2.html\",\n  \"progressWithinChapter\": 0.5\n}\n"

  private val bookmarkBody0 =
    BookmarkAnnotationBodyNode(
      timestamp = "2019-01-25T20:00:37+0000",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = 50.0f
    )

  private val bookmarkBody1 =
    BookmarkAnnotationBodyNode(
      timestamp = "2019-01-25T20:00:37+0000",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = 50.0f
    )

  private val bookmarkBody2 =
    BookmarkAnnotationBodyNode(
      timestamp = "2019-01-25T20:00:37+0000",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = 50.0f
    )

  private val bookmarkBodyBadDate =
    BookmarkAnnotationBodyNode(
      timestamp = "2019-01-25T20:00:37Z",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = 0.0f
    )

  private val bookmark0 =
    BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = this.bookmarkBody0,
      id = "x",
      type = "Annotation",
      motivation = "http://www.w3.org/ns/oa#bookmarking",
      target = BookmarkAnnotationTargetNode(
        "z0",
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0)
      )
    )

  private val bookmark1 =
    BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = this.bookmarkBody1,
      id = "x",
      type = "Annotation",
      motivation = "http://www.w3.org/ns/oa#bookmarking",
      target = BookmarkAnnotationTargetNode(
        "z1",
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue1)
      )
    )

  private val bookmark2 =
    BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = this.bookmarkBody2,
      id = "x",
      type = "Annotation",
      motivation = "http://www.w3.org/ns/oa#bookmarking",
      target = BookmarkAnnotationTargetNode(
        "z2",
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue2)
      )
    )

  private val bookmarkAnnotationResponse =
    BookmarkAnnotationResponse(
      context = listOf("c0", "c1", "c2"),
      total = 20,
      type = listOf("t0", "t1", "t2"),
      id = "id0",
      first = BookmarkAnnotationFirstNode(
        items = listOf(this.bookmark0, this.bookmark1, this.bookmark2),
        type = "Annotation",
        id = "id"
      )
    )

  @Test
  fun testSelector() {
    val input =
      BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0)
    val node =
      BookmarkAnnotationsJSON.serializeSelectorNodeToJSON(this.objectMapper, input)

    assertEquals("oa:FragmentSelector", node["type"].textValue())
    assertEquals(this.targetValue0, node["value"].textValue())

    assertEquals(input, BookmarkAnnotationsJSON.deserializeSelectorNodeFromJSON(this.objectMapper, node))
  }

  @Test
  fun testTarget() {
    val input =
      BookmarkAnnotationTargetNode("z", BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0))
    val node =
      BookmarkAnnotationsJSON.serializeTargetNodeToJSON(this.objectMapper, input)

    assertEquals("z", node["source"].textValue())
    assertEquals("oa:FragmentSelector", node["selector"]["type"].textValue())
    assertEquals(this.targetValue0, node["selector"]["value"].textValue())

    assertEquals(input, BookmarkAnnotationsJSON.deserializeTargetNodeFromJSON(this.objectMapper, node))
  }

  @Test
  fun testBody() {
    val node =
      BookmarkAnnotationsJSON.serializeBodyNodeToJSON(this.objectMapper, this.bookmarkBody0)

    assertEquals(
      "2019-01-25T20:00:37+0000",
      node["http://librarysimplified.org/terms/time"].textValue()
    )
    assertEquals(
      "cca80416-3168-4e58-b621-7964b9265ac9",
      node["http://librarysimplified.org/terms/device"].textValue()
    )

    assertNull(node["http://librarysimplified.org/terms/progressWithinChapter"])

    assertEquals(
      50.0,
      node["http://librarysimplified.org/terms/progressWithinBook"].doubleValue(),
      0.0
    )

    assertEquals(
      "A Title",
      node["http://librarysimplified.org/terms/chapter"].textValue()
    )

    assertEquals(this.bookmarkBody0, BookmarkAnnotationsJSON.deserializeBodyNodeFromJSON(node))
  }

  @Test
  fun testBookmark() {
    val target =
      BookmarkAnnotationTargetNode("z", BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0))

    val input =
      BookmarkAnnotation(
        context = "http://www.w3.org/ns/anno.jsonld",
        body = this.bookmarkBody0,
        id = "x",
        type = "Annotation",
        motivation = "http://www.w3.org/ns/oa#bookmarking",
        target = target
      )

    val node =
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationToJSON(this.objectMapper, input)

    this.compareAnnotations(input, BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(this.objectMapper, node))
  }

  @Test
  fun testBookmarkAnnotationFirstNode() {
    val input =
      BookmarkAnnotationFirstNode(
        type = "x",
        id = "z",
        items = listOf(this.bookmark0, this.bookmark1, this.bookmark2)
      )

    val node =
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationFirstNodeToJSON(this.objectMapper, input)

    assertEquals(
      input,
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFirstNodeFromJSON(this.objectMapper, node)
    )
  }

  @Test
  fun testBookmarkAnnotationResponse() {
    val node =
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationResponseToJSON(
        this.objectMapper, this.bookmarkAnnotationResponse
      )

    assertEquals(
      this.bookmarkAnnotationResponse,
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationResponseFromJSON(
        objectMapper = this.objectMapper,
        node = node
      )
    )
  }

  @Test
  fun testBookmarkBadDateSIMPLY_1938() {
    val target =
      BookmarkAnnotationTargetNode("z", BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0))

    val input =
      BookmarkAnnotation(
        context = "http://www.w3.org/ns/anno.jsonld",
        body = this.bookmarkBodyBadDate,
        id = "x",
        type = "Annotation",
        motivation = "http://www.w3.org/ns/oa#bookmarking",
        target = target
      )

    val node =
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationToJSON(this.objectMapper, input)

    this.compareAnnotations(input, BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(this.objectMapper, node))
  }

  @Test
  fun testSpecValidBookmark0() {
    val annotation =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("valid-bookmark-0.json")
      )

    val bookmark = BookmarkAnnotations.toSerializedBookmark(this.objectMapper, annotation)
    assertEquals("urn:uuid:1daa8de6-94e8-4711-b7d1-e43b572aa6e0", bookmark.opdsId)
    assertEquals("urn:uuid:c83db5b1-9130-4b86-93ea-634b00235c7c", bookmark.deviceID)
    assertEquals(BookmarkKind.BookmarkLastReadLocation, bookmark.kind)
    assertEquals("2021-03-12T16:32:49.000Z", bookmark.time.toString())
    assertEquals("", bookmark.bookChapterTitle)

    val location = bookmark.location as SerializedLocatorHrefProgression20210317
    assertEquals(0.666, location.chapterProgress, 0.0)
    assertEquals("/xyz.html", location.chapterHref)

    this.checkRoundTrip(annotation)
  }

  @Test
  fun testSpecValidBookmark1() {
    val annotation =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("valid-bookmark-1.json")
      )

    DateTimeUtils.setCurrentMillisFixed(0L)

    val bookmark = BookmarkAnnotations.toSerializedBookmark(this.objectMapper, annotation)
    assertEquals("urn:uuid:1daa8de6-94e8-4711-b7d1-e43b572aa6e0", bookmark.opdsId)
    assertEquals("urn:uuid:c83db5b1-9130-4b86-93ea-634b00235c7c", bookmark.deviceID)
    assertEquals(BookmarkKind.BookmarkLastReadLocation, bookmark.kind)
    assertEquals("2021-03-12T16:32:49.000Z", bookmark.time.toString())
    assertEquals("", bookmark.bookChapterTitle)

    val location = bookmark.location as SerializedLocatorHrefProgression20210317
    assertEquals(0.666, location.chapterProgress, 0.0)
    assertEquals("/xyz.html", location.chapterHref)

    this.checkRoundTrip(annotation)
  }

  @Test
  fun testSpecValidBookmark2() {
    val annotation =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("valid-bookmark-2.json")
      )

    DateTimeUtils.setCurrentMillisFixed(0L)

    val bookmark = BookmarkAnnotations.toSerializedBookmark(this.objectMapper, annotation)
    assertEquals("urn:uuid:1daa8de6-94e8-4711-b7d1-e43b572aa6e0", bookmark.opdsId)
    assertEquals("urn:uuid:c83db5b1-9130-4b86-93ea-634b00235c7c", bookmark.deviceID)
    assertEquals(BookmarkKind.BookmarkExplicit, bookmark.kind)
    assertEquals("2021-03-12T16:32:49.000Z", bookmark.time.toString())
    assertEquals("", bookmark.bookChapterTitle)

    val location = bookmark.location as SerializedLocatorHrefProgression20210317
    assertEquals(0.666, location.chapterProgress, 0.0)
    assertEquals("/xyz.html", location.chapterHref)

    this.checkRoundTrip(annotation)
  }

  @Test
  fun testSpecValidBookmark3() {
    val annotation =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("valid-bookmark-3.json")
      )

    DateTimeUtils.setCurrentMillisFixed(0L)

    val bookmark = BookmarkAnnotations.toSerializedBookmark(this.objectMapper, annotation)
    assertEquals("urn:uuid:1daa8de6-94e8-4711-b7d1-e43b572aa6e0", bookmark.opdsId)
    assertEquals("urn:uuid:c83db5b1-9130-4b86-93ea-634b00235c7c", bookmark.deviceID)
    assertEquals(BookmarkKind.BookmarkExplicit, bookmark.kind)
    assertEquals("2021-03-12T16:32:49.000Z", bookmark.time.toString())
    assertEquals("", bookmark.bookChapterTitle)

    val location = bookmark.location as SerializedLocatorHrefProgression20210317
    assertEquals(0.666, location.chapterProgress, 0.0)
    assertEquals("/xyz.html", location.chapterHref)

    this.checkRoundTrip(annotation)
  }

  @Test
  fun testSpecValidLocator0() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-0.json"))

    val locationHP = location as SerializedLocatorHrefProgression20210317
    assertEquals(0.666, locationHP.chapterProgress, 0.0)
    assertEquals("/xyz.html", locationHP.chapterHref)
  }

  @Test
  fun testSpecValidLocator1() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-1.json"))

    val locationR1 = location as SerializedLocatorLegacyCFI
    assertEquals(0.25, locationR1.chapterProgression, 0.0)
    assertEquals("xyz-html", locationR1.idRef)
    assertEquals("/4/2/2/2", locationR1.contentCFI)
  }

  @Test
  fun testSpecValidLocator2() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-2.json"))

    val locationR1 = location as SerializedLocatorPage1
    assertEquals(23, locationR1.page)
  }

  @Test
  fun testSpecValidLocator3() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-3.json"))

    val locationR1 = location as SerializedLocatorAudioBookTime1
    assertEquals(78000, locationR1.timeMilliseconds)
  }

  @Test
  fun testSpecValidLocator4() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-4.json"))

    val locationR1 = location as SerializedLocatorAudioBookTime1
    assertEquals(63000, locationR1.timeWithoutOffset)
  }

  @Test
  fun testSpecValidLocator5() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-5.json"))

    val locationR1 = location as SerializedLocatorAudioBookTime1
    assertEquals(78000, locationR1.timeMilliseconds)
  }

  @Test
  fun testSpecValidLocator6() {
    val location =
      SerializedLocators.parseLocatorFromString(this.resourceText("valid-locator-6.json"))

    val locationR1 = location as SerializedLocatorAudioBookTime2
    assertEquals(25000, locationR1.readingOrderItemOffsetMilliseconds)
  }

  @Test
  fun testSpecInvalidLocator1() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-1.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Expected: A key 'href'"))
  }

  @Test
  fun testSpecInvalidLocator2() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-2.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Expected: A key 'progressWithinChapter'"))
  }

  @Test
  fun testSpecInvalidLocator3() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-3.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Chapter progress -1.0 must be in [0.0, 1.0]"))
  }

  @Test
  fun testSpecInvalidLocator4() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-4.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Chapter progress 2.0 must be in [0.0, 1.0]"))
  }

  @Test
  fun testSpecInvalidLocator5() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-5.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Chapter -5 must be non-negative."))
  }

  @Test
  fun testSpecInvalidLocator6() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-6.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Page number -3 must be non-negative."))
  }

  @Test
  fun testSpecInvalidLocator7() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-7.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Expected: A key 'time'"))
  }

  @Test
  fun testSpecInvalidLocator8() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-8.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Expected: A key 'readingOrderItem'"))
  }

  @Test
  fun testSpecInvalidLocator9() {
    val ex = assertThrows(JSONParseException::class.java) {
      SerializedLocators.parseLocatorFromString(this.resourceText("invalid-locator-9.json"))
    }
    this.logger.debug("Message: {}", ex.message)
    assertTrue(ex.message!!.contains("Expected: A key 'readingOrderItemOffsetMilliseconds'"))
  }

  @Test
  fun testSpecInvalidBookmark0() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-0.json")
      )
    }
    assertTrue(ex.message!!.contains("Expected: A key 'body'"))
  }

  @Test
  fun testSpecInvalidBookmark1() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-1.json")
      )
    }
    assertTrue(ex.message!!.contains("Expected: A key 'motivation'"))
  }

  @Test
  fun testSpecInvalidBookmark2() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-2.json")
      )
    }
    assertTrue(ex.message!!.contains("Expected: A key 'target'"))
  }

  @Test
  fun testSpecInvalidBookmark3() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-3.json")
      )
    }
    assertTrue(ex.message!!.contains("Unrecognized selector node type: What?"))
  }

  @Test
  fun testSpecInvalidBookmark4() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-4.json")
      )
    }
    assertTrue(ex.message!!.contains("Unexpected character ('['"))
  }

  @Test
  fun testSpecInvalidBookmark5() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-5.json")
      )
    }
    assertTrue(ex.message!!.contains("Expected: A key 'http://librarysimplified.org/terms/device'"))
  }

  @Test
  fun testSpecInvalidBookmark6() {
    val ex = assertThrows(JSONParseException::class.java) {
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("invalid-bookmark-6.json")
      )
    }
    assertTrue(ex.message!!.contains("Expected: A key 'http://librarysimplified.org/terms/time'"))
  }

  private fun resourceText(
    name: String
  ): String {
    return this.resource(name).readBytes().decodeToString()
  }

  private fun resourceNode(
    name: String
  ): ObjectNode {
    return this.objectMapper.readTree(this.resourceText(name)) as ObjectNode
  }

  private fun checkRoundTrip(bookmarkAnnotation: BookmarkAnnotation) {
    val serialized =
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationToBytes(this.objectMapper, bookmarkAnnotation)
    val serializedText =
      serialized.decodeToString()

    this.logger.debug("serialized: {}", serializedText)

    val deserialized =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        this.objectMapper,
        this.objectMapper.readTree(serialized) as ObjectNode
      )

    this.compareAnnotations(bookmarkAnnotation, deserialized)

    val toBookmark =
      BookmarkAnnotations.toSerializedBookmark(this.objectMapper, deserialized)
    val fromBookmark =
      BookmarkAnnotations.fromSerializedBookmark(this.objectMapper, toBookmark)
    val toBookmarkAgain =
      BookmarkAnnotations.toSerializedBookmark(this.objectMapper, fromBookmark)

    this.compareAnnotations(bookmarkAnnotation, deserialized)
    this.compareAnnotations(bookmarkAnnotation, fromBookmark)
    assertEquals(toBookmark, toBookmarkAgain)
  }

  private fun compareAnnotations(
    x: BookmarkAnnotation,
    y: BookmarkAnnotation
  ) {
    this.logger.debug("compareAnnotations: x: {}", x)
    this.logger.debug("compareAnnotations: y: {}", y)

    assertEquals(x.body.bookProgress, y.body.bookProgress)
    assertEquals(x.body.chapterTitle, y.body.chapterTitle)
    assertEquals(x.body.device, y.body.device)
    assertEquals(x.body.timestamp, y.body.timestamp)
    assertEquals(x.context, y.context)
    assertEquals(x.id, y.id)
    assertEquals(x.kind, y.kind)
    assertEquals(x.motivation, y.motivation)
    assertEquals(x.target.selector.type, y.target.selector.type)

    val xSelectorValue =
      SerializedLocators.parseLocatorFromString(x.target.selector.value)
    val ySelectorValue =
      SerializedLocators.parseLocatorFromString(y.target.selector.value)

    assertEquals(xSelectorValue, ySelectorValue)
    assertEquals(x.target.source, y.target.source)
    assertEquals(x.type, y.type)
  }

  private fun resource(
    name: String
  ): InputStream {
    val fileName =
      "/org/nypl/simplified/tests/bookmark_annotations/spec/bookmarks/$name"
    val url =
      ReaderBookmarkAnnotationsJSONTest::class.java.getResource(fileName)
        ?: throw FileNotFoundException("No such resource: $fileName")
    return url.openStream()
  }
}
