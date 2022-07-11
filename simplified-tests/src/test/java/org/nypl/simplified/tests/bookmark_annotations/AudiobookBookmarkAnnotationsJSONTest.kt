package org.nypl.simplified.tests.bookmark_annotations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nypl.simplified.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationBodyNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationFirstNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationResponse
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationSelectorNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationsJSON
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationTargetNode
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.InputStream

class AudiobookBookmarkAnnotationsJSONTest {

  private val logger =
    LoggerFactory.getLogger(AudiobookBookmarkAnnotationsJSONTest::class.java)

  private val objectMapper: ObjectMapper = ObjectMapper()
  private val targetValue0 =
    "{\n \"title\": \"Chapter title\",\n \"chapter\": 1,\n  \"part\": 1,\n  \"time\":123,\n" +
      "\"duration\": \"190\",\n \"audiobookID\": \"urn:uuid:b309844e-7d4e-403e-945b-fbc78acd5e03\"\n}\n"
  private val targetValue1 =
    "{\n \"title\": \"Chapter title 2\",\n  \"chapter\": 2,\n  \"part\": 1,\n  \"time\":111,\n" +
      "\"duration\": \"190\",\n \"audiobookID\": \"urn:uuid:b309844e-7d4e-403e-945b-fbc78acd5e03\"\n}\n"
  private val targetValue2 =
    "{\n \"title\": \"Chapter title 3\",\n  \"chapter\": 3,\n  \"part\": 1,\n  \"time\":100\n,\n" +
      "\"duration\": \"190\",\n \"audiobookID\": \"urn:uuid:b309844e-7d4e-403e-945b-fbc78acd5e03\"\n}\n"

  private val bookmarkBody0 =
    BookmarkAnnotationBodyNode(
      timestamp = "2022-06-27T12:39:37+0000",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = null
    )

  private val bookmarkBody1 =
    BookmarkAnnotationBodyNode(
      timestamp = "2022-06-27T12:39:37+0000",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = null
    )

  private val bookmarkBody2 =
    BookmarkAnnotationBodyNode(
      timestamp = "2022-06-27T12:39:37+0000",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = null
    )

  private val bookmarkBodyBadDate =
    BookmarkAnnotationBodyNode(
      timestamp = "2022-06-27T20:00:37Z",
      device = "cca80416-3168-4e58-b621-7964b9265ac9",
      chapterTitle = "A Title",
      bookProgress = null
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

    assertEquals(
      input,
      BookmarkAnnotationsJSON.deserializeSelectorNodeFromJSON(this.objectMapper, node)
    )
  }

  @Test
  fun testTarget() {
    val input =
      BookmarkAnnotationTargetNode(
        "z",
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0)
      )
    val node =
      BookmarkAnnotationsJSON.serializeTargetNodeToJSON(this.objectMapper, input)

    assertEquals("z", node["source"].textValue())
    assertEquals("oa:FragmentSelector", node["selector"]["type"].textValue())
    assertEquals(this.targetValue0, node["selector"]["value"].textValue())

    assertEquals(
      input,
      BookmarkAnnotationsJSON.deserializeTargetNodeFromJSON(this.objectMapper, node)
    )
  }

  @Test
  fun testBody() {
    val node =
      BookmarkAnnotationsJSON.serializeBodyNodeToJSON(this.objectMapper, this.bookmarkBody0)

    assertEquals(
      "2022-06-27T12:39:37+0000",
      node["http://librarysimplified.org/terms/time"].textValue()
    )
    assertEquals(
      "cca80416-3168-4e58-b621-7964b9265ac9",
      node["http://librarysimplified.org/terms/device"].textValue()
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
      BookmarkAnnotationTargetNode(
        "z",
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0)
      )

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

    this.compareAnnotations(
      input,
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(this.objectMapper, node)
    )
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
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFirstNodeFromJSON(
        this.objectMapper,
        node
      )
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
      BookmarkAnnotationTargetNode(
        "z",
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", this.targetValue0)
      )

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

    this.compareAnnotations(
      input,
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(this.objectMapper, node)
    )
  }

  @Test
  fun testSpecValidBookmark() {
    val annotation =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationFromJSON(
        objectMapper = this.objectMapper,
        node = this.resourceNode("valid-bookmark-4.json")
      )

    val bookmark = BookmarkAnnotations.toAudiobookBookmark(this.objectMapper, annotation)
    assertEquals("urn:uuid:1daa8de6-94e8-4711-b7d1-e43b572aa6e0", bookmark.opdsId)
    assertEquals("urn:uuid:c83db5b1-9130-4b86-93ea-634b00235c7c", bookmark.deviceID)
    assertEquals(BookmarkKind.BookmarkLastReadLocation, bookmark.kind)
    assertEquals("2022-06-27T12:47:49.000Z", bookmark.time.toString())

    val location = bookmark.location
    assertEquals("Chapter title", location.title)
    assertEquals(32, location.chapter)
    assertEquals(3, location.part)
    assertEquals(78000, location.offsetMilliseconds)

    this.checkRoundTrip(annotation)
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
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationToBytes(
        this.objectMapper,
        bookmarkAnnotation
      )
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
      BookmarkAnnotations.toAudiobookBookmark(this.objectMapper, deserialized)
    val fromBookmark =
      BookmarkAnnotations.fromAudiobookBookmark(this.objectMapper, toBookmark)
    val toBookmarkAgain =
      BookmarkAnnotations.toAudiobookBookmark(this.objectMapper, fromBookmark)

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
      BookmarkAnnotationsJSON.deserializeAudiobookLocation(
        this.objectMapper,
        x.target.selector.value
      )
    val ySelectorValue =
      BookmarkAnnotationsJSON.deserializeAudiobookLocation(
        this.objectMapper,
        y.target.selector.value
      )

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
      AudiobookBookmarkAnnotationsJSONTest::class.java.getResource(fileName)
        ?: throw FileNotFoundException("No such resource: $fileName")
    return url.openStream()
  }
}
