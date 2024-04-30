package org.nypl.simplified.bookmarks.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmark20210828
import org.nypl.simplified.books.api.bookmark.SerializedLocators
import java.net.URI

data class BookmarkAnnotationSelectorNode(
  val type: String,
  val value: String
)

data class BookmarkAnnotationTargetNode(
  val source: String,
  val selector: BookmarkAnnotationSelectorNode
)

data class BookmarkAnnotationBodyNode(
  val timestamp: String,
  val device: String,
  val chapterTitle: String = "",
  val bookProgress: Float = 0.0f
)

data class BookmarkAnnotation(
  val context: String?,
  val body: BookmarkAnnotationBodyNode,
  val id: String?,
  val type: String,
  val motivation: String,
  val target: BookmarkAnnotationTargetNode
) {
  override fun equals(other: Any?): Boolean {
    return this.target.selector.value == (other as BookmarkAnnotation).target.selector.value
  }

  override fun hashCode(): Int {
    return this.target.selector.value.hashCode()
  }

  val kind: BookmarkKind =
    BookmarkKind.ofMotivation(this.motivation)
}

data class BookmarkAnnotationFirstNode(
  val items: List<BookmarkAnnotation>,
  val type: String,
  val id: String
)

data class BookmarkAnnotationResponse(
  val context: List<String>,
  val total: Int,
  val type: List<String>,
  val id: String,
  val first: BookmarkAnnotationFirstNode
)

object BookmarkAnnotations {

  private val dateParser =
    ISODateTimeFormat.dateTimeParser()
  private val dateFormatter =
    DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  fun fromSerializedBookmark(
    objectMapper: ObjectMapper,
    serializedBookmark: SerializedBookmark
  ): BookmarkAnnotation {
    val timestamp =
      this.dateFormatter.print(serializedBookmark.time)

    val bodyAnnotation =
      BookmarkAnnotationBodyNode(
        timestamp = timestamp,
        device = serializedBookmark.deviceID,
        chapterTitle = serializedBookmark.bookChapterTitle,
        bookProgress = serializedBookmark.bookProgress.toFloat()
      )

    val locationJSON =
      serializedBookmark.location.toJSONString(objectMapper)

    val target =
      BookmarkAnnotationTargetNode(
        serializedBookmark.opdsId,
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", locationJSON)
      )

    return BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = bodyAnnotation,
      id = serializedBookmark.uri?.toString(),
      type = "Annotation",
      motivation = serializedBookmark.kind.motivationURI,
      target = target
    )
  }

  fun toSerializedBookmark(
    objectMapper: ObjectMapper,
    annotation: BookmarkAnnotation
  ): SerializedBookmark {
    val location =
      SerializedLocators.parseLocator(objectMapper.readTree(annotation.target.selector.value))

    return SerializedBookmark20210828(
      deviceID = annotation.body.device,
      kind = annotation.kind,
      location = location,
      opdsId = annotation.target.source,
      time = this.dateParser.parseDateTime(annotation.body.timestamp),
      uri = if (annotation.id != null) URI.create(annotation.id) else null,
      bookProgress = annotation.body.bookProgress.toDouble(),
      bookChapterProgress = 0.0,
      bookChapterTitle = annotation.body.chapterTitle,
      bookTitle = ""
    )
  }
}
