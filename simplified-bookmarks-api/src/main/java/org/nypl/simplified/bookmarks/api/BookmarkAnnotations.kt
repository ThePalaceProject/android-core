package org.nypl.simplified.bookmarks.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
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
  val chapterTitle: String?,
  val bookProgress: Float?
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

  fun fromReaderBookmark(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.ReaderBookmark
  ): BookmarkAnnotation {
    /*
     * Check for some values that were likely added by [toBookmark]. Write special values here
     * to ensure that [fromBookmark] is the exact inverse of [toBookmark].
     */

    val chapterTitle =
      if (bookmark.chapterTitle == "") {
        null
      } else {
        bookmark.chapterTitle
      }

    val bookProgress =
      if (bookmark.bookProgress == 0.0) {
        null
      } else {
        bookmark.bookProgress?.toFloat()
      }

    val timestamp =
      dateFormatter.print(bookmark.time)

    val bodyAnnotation =
      BookmarkAnnotationBodyNode(
        timestamp = timestamp,
        device = bookmark.deviceID,
        chapterTitle = chapterTitle,
        bookProgress = bookProgress
      )

    val locationJSON =
      BookmarkAnnotationsJSON.serializeBookmarkLocation(
        objectMapper = objectMapper,
        bookmark = bookmark
      )

    val target =
      BookmarkAnnotationTargetNode(
        bookmark.opdsId,
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", locationJSON)
      )

    return BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = bodyAnnotation,
      id = bookmark.uri?.toString(),
      type = "Annotation",
      motivation = bookmark.kind.motivationURI,
      target = target
    )
  }

  fun toReaderBookmark(
    objectMapper: ObjectMapper,
    annotation: BookmarkAnnotation
  ): Bookmark.ReaderBookmark {
    val locationJSON =
      BookmarkAnnotationsJSON.deserializeReaderLocation(
        objectMapper = objectMapper,
        value = annotation.target.selector.value
      )

    val time =
      if (annotation.body.timestamp != null) {
        dateParser.parseDateTime(annotation.body.timestamp)
      } else {
        DateTime.now(DateTimeZone.UTC)
      }

    return Bookmark.ReaderBookmark.create(
      opdsId = annotation.target.source,
      location = locationJSON,
      kind = BookmarkKind.ofMotivation(annotation.motivation),
      time = time,
      chapterTitle = annotation.body.chapterTitle ?: "",
      bookProgress = annotation.body.bookProgress?.toDouble(),
      uri = if (annotation.id != null) URI.create(annotation.id) else null,
      deviceID = annotation.body.device
    )
  }

  fun fromAudiobookBookmark(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.AudiobookBookmark
  ): BookmarkAnnotation {
    /*
     * Check for some values that were likely added by [toBookmark]. Write special values here
     * to ensure that [fromBookmark] is the exact inverse of [toBookmark].
     */

    val timestamp =
      this.dateFormatter.print(bookmark.time)

    val bodyAnnotation =
      BookmarkAnnotationBodyNode(
        timestamp = timestamp,
        device = bookmark.deviceID,
        chapterTitle = bookmark.location.title.orEmpty(),
        bookProgress = null
      )

    val locationJSON =
      BookmarkAnnotationsJSON.serializeBookmarkLocation(
        objectMapper = objectMapper,
        bookmark = bookmark
      )

    val target =
      BookmarkAnnotationTargetNode(
        bookmark.opdsId,
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", locationJSON)
      )

    return BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = bodyAnnotation,
      id = bookmark.uri?.toString(),
      type = "Annotation",
      motivation = bookmark.kind.motivationURI,
      target = target
    )
  }

  fun toAudiobookBookmark(
    objectMapper: ObjectMapper,
    annotation: BookmarkAnnotation
  ): Bookmark.AudiobookBookmark {
    val locationJSON =
      BookmarkAnnotationsJSON.deserializeAudiobookLocation(
        objectMapper = objectMapper,
        value = annotation.target.selector.value
      )

    val duration =
      BookmarkAnnotationsJSON.deserializeAudiobookDuration(
        objectMapper = objectMapper,
        value = annotation.target.selector.value
      )

    val time =
      if (annotation.body.timestamp != null) {
        this.dateParser.parseDateTime(annotation.body.timestamp)
      } else {
        DateTime.now(DateTimeZone.UTC)
      }

    return Bookmark.AudiobookBookmark.create(
      opdsId = annotation.target.source,
      location = locationJSON,
      duration = duration,
      kind = BookmarkKind.ofMotivation(annotation.motivation),
      time = time,
      uri = if (annotation.id != null) URI.create(annotation.id) else null,
      deviceID = annotation.body.device
    )
  }

  fun fromPdfBookmark(
    objectMapper: ObjectMapper,
    bookmark: Bookmark.PDFBookmark
  ): BookmarkAnnotation {
    /*
     * Check for some values that were likely added by [toBookmark]. Write special values here
     * to ensure that [fromBookmark] is the exact inverse of [toBookmark].
     */

    val chapterTitle = null
    val bookProgress = null

    val timestamp =
      dateFormatter.print(bookmark.time)

    val bodyAnnotation =
      BookmarkAnnotationBodyNode(
        timestamp = timestamp,
        device = bookmark.deviceID,
        chapterTitle = chapterTitle,
        bookProgress = bookProgress
      )

    val locationJSON =
      BookmarkAnnotationsJSON.serializeBookmarkLocation(
        objectMapper = objectMapper,
        bookmark = bookmark
      )

    val target =
      BookmarkAnnotationTargetNode(
        bookmark.opdsId,
        BookmarkAnnotationSelectorNode("oa:FragmentSelector", locationJSON)
      )

    return BookmarkAnnotation(
      context = "http://www.w3.org/ns/anno.jsonld",
      body = bodyAnnotation,
      id = bookmark.uri?.toString(),
      type = "Annotation",
      motivation = bookmark.kind.motivationURI,
      target = target
    )
  }

  fun toPdfBookmark(
    objectMapper: ObjectMapper,
    annotation: BookmarkAnnotation
  ): Bookmark.PDFBookmark {
    val locationJSON =
      BookmarkAnnotationsJSON.deserializePdfLocation(
        objectMapper = objectMapper,
        value = annotation.target.selector.value
      )

    val time =
      if (annotation.body.timestamp != null) {
        dateParser.parseDateTime(annotation.body.timestamp)
      } else {
        DateTime.now(DateTimeZone.UTC)
      }

    return Bookmark.PDFBookmark.create(
      opdsId = annotation.target.source,
      kind = BookmarkKind.ofMotivation(annotation.motivation),
      time = time,
      pageNumber = locationJSON,
      uri = if (annotation.id != null) URI.create(annotation.id) else null,
      deviceID = annotation.body.device
    )
  }
}
