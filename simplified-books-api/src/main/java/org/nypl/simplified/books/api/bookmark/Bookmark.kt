package org.nypl.simplified.books.api.bookmark

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.librarysimplified.audiobook.api.PlayerPosition
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.api.BookLocation
import java.io.Serializable
import java.net.URI
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * The saved data for a bookmark.
 */

sealed class Bookmark {

  /**
   * @return The identifier of the book taken from the OPDS entry that provided it.
   */

  abstract val opdsId: String

  /**
   * @return The kind of bookmark.
   */

  abstract val kind: BookmarkKind

  /**
   * @return The time the bookmark was created.
   */

  abstract val time: DateTime

  /**
   * @return The identifier of the device that created the bookmark, if one is available.
   */

  abstract val deviceID: String

  /**
   * @return The URI of this bookmark, if the bookmark exists on a remote server.
   */

  abstract val uri: URI?

  /**
   * The ID of the book to which the bookmark belongs.
   */

  abstract val book: BookID

  /**
   * The unique ID of the bookmark.
   */

  abstract val bookmarkId: BookmarkID

  /**
   * Convenience function to convert a bookmark to a last-read-location kind.
   */
  abstract fun toLastReadLocation(): Bookmark

  /**
   * Convenience function to convert a bookmark to an explicit kind.
   */

  abstract fun toExplicit(): Bookmark

  /**
   * Class for bookmarks of reader type.
   *
   * <p>Note: The type is {@link Serializable} purely because the Android API requires this
   * in order pass values of this type between activities. We make absolutely no guarantees
   * that serialized values of this class will be compatible with future releases.</p>
   */

  data class ReaderBookmark(
    override val opdsId: String,
    override val time: DateTime,
    override val deviceID: String,
    override val kind: BookmarkKind,
    override val uri: URI?,

    /**
     * The title of the chapter.
     */

    val chapterTitle: String,

    /**
     * The location of the bookmark.
     */

    val location: BookLocation,

    /**
     * An estimate of the current book progress, in the range [0, 1]
     */

    @Deprecated("Use progress information from the BookLocation")
    val bookProgress: Double?

  ) : Bookmark(), Serializable {

    override val book: BookID = BookIDs.newFromText(this.opdsId)

    override val bookmarkId: BookmarkID = createBookmarkID(this.book, this.location, this.kind)

    override fun toLastReadLocation(): Bookmark {
      return this.copy(kind = BookmarkKind.BookmarkLastReadLocation)
    }

    override fun toExplicit(): Bookmark {
      return this.copy(kind = BookmarkKind.BookmarkExplicit)
    }

    init {
      check(this.time.zone == DateTimeZone.UTC) {
        "Bookmark time zones must be UTC"
      }
    }

    /**
     * An estimate of the current chapter progress, in the range [0, 1]
     */

    val chapterProgress: Double =
      when (this.location) {
        is BookLocation.BookLocationR2 -> this.location.progress.chapterProgress
        is BookLocation.BookLocationR1 -> this.location.progress ?: 0.0
      }

    private fun createBookmarkID(
      book: BookID,
      location: BookLocation,
      kind: BookmarkKind
    ): BookmarkID {
      try {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val utf8 = Charset.forName("UTF-8")
        messageDigest.update(book.value().toByteArray(utf8))

        when (location) {
          is BookLocation.BookLocationR2 -> {
            val chapterProgress = location.progress
            messageDigest.update(chapterProgress.chapterHref.toByteArray(utf8))
            val truncatedProgress = String.format("%.6f", chapterProgress.chapterProgress)
            messageDigest.update(truncatedProgress.toByteArray(utf8))
          }
          is BookLocation.BookLocationR1 -> {
            val cfi = location.contentCFI
            if (cfi != null) {
              messageDigest.update(cfi.toByteArray(utf8))
            }
            val idRef = location.idRef
            if (idRef != null) {
              messageDigest.update(idRef.toByteArray(utf8))
            }
          }
        }

        messageDigest.update(kind.motivationURI.toByteArray(utf8))

        val digestResult = messageDigest.digest()
        val builder = StringBuilder(64)
        for (index in digestResult.indices) {
          val bb = digestResult[index]
          builder.append(String.format("%02x", bb))
        }

        return BookmarkID(builder.toString())
      } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException(e)
      }
    }

    companion object {

      fun create(
        opdsId: String,
        location: BookLocation,
        kind: BookmarkKind,
        time: DateTime,
        chapterTitle: String,
        bookProgress: Double?,
        deviceID: String,
        uri: URI?
      ): ReaderBookmark {
        return ReaderBookmark(
          opdsId = opdsId,
          location = location,
          kind = kind,
          time = time.toDateTime(DateTimeZone.UTC),
          chapterTitle = chapterTitle,
          bookProgress = bookProgress,
          deviceID = deviceID,
          uri = uri
        )
      }
    }
  }

  /**
   * Class for bookmarks of PDF type.
   *
   * <p>Note: The type is {@link Serializable} purely because the Android API requires this
   * in order pass values of this type between activities. We make absolutely no guarantees
   * that serialized values of this class will be compatible with future releases.</p>
   */

  data class PDFBookmark(
    override val opdsId: String,
    override val time: DateTime,
    override val deviceID: String,
    override val kind: BookmarkKind,
    override val uri: URI?,
    val pageNumber: Int
  ) : Bookmark(), Serializable {

    override val book: BookID = BookIDs.newFromText(this.opdsId)

    override val bookmarkId: BookmarkID = createBookmarkID(this.book, this.kind, this.pageNumber)

    override fun toLastReadLocation(): Bookmark {
      return this.copy(kind = BookmarkKind.BookmarkLastReadLocation)
    }

    override fun toExplicit(): Bookmark {
      return this.copy(kind = BookmarkKind.BookmarkExplicit)
    }

    init {
      check(this.time.zone == DateTimeZone.UTC) {
        "Bookmark time zones must be UTC"
      }
    }

    /**
     * Create a bookmark ID from the given book ID, kind and page number.
     */

    private fun createBookmarkID(
      book: BookID,
      kind: BookmarkKind,
      pageNumber: Int
    ): BookmarkID {
      try {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val utf8 = Charset.forName("UTF-8")
        messageDigest.update(book.value().toByteArray(utf8))
        messageDigest.update(kind.motivationURI.toByteArray(utf8))
        messageDigest.update(pageNumber.toString().toByteArray(utf8))

        val digestResult = messageDigest.digest()
        val builder = StringBuilder(64)
        for (index in digestResult.indices) {
          val bb = digestResult[index]
          builder.append(String.format("%02x", bb))
        }

        return BookmarkID(builder.toString())
      } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException(e)
      }
    }

    companion object {

      fun create(
        opdsId: String,
        kind: BookmarkKind,
        time: DateTime,
        pageNumber: Int,
        deviceID: String,
        uri: URI?
      ): PDFBookmark {
        return PDFBookmark(
          opdsId = opdsId,
          pageNumber = pageNumber,
          kind = kind,
          time = time.toDateTime(DateTimeZone.UTC),
          deviceID = deviceID,
          uri = uri
        )
      }
    }
  }

  /**
   * Class for bookmarks of audiobook type.
   *
   * <p>Note: The type is {@link Serializable} purely because the Android API requires this
   * in order pass values of this type between activities. We make absolutely no guarantees
   * that serialized values of this class will be compatible with future releases.</p>
   */

  data class AudiobookBookmark(
    override val opdsId: String,
    override val time: DateTime,
    override val deviceID: String,
    override val kind: BookmarkKind,
    override val uri: URI?,

    /**
     * The location of the bookmark.
     */

    val location: PlayerPosition,

    /**
     * The duration of the bookmark's chapter.
     */
    val duration: Long

  ) : Bookmark() {

    override val book: BookID = BookIDs.newFromText(this.opdsId)

    override val bookmarkId: BookmarkID = createBookmarkID(this.book, this.kind, this.location)

    override fun toLastReadLocation(): Bookmark {
      return this.copy(kind = BookmarkKind.BookmarkLastReadLocation)
    }

    override fun toExplicit(): Bookmark {
      return this.copy(kind = BookmarkKind.BookmarkExplicit)
    }

    init {
      check(this.time.zone == DateTimeZone.UTC) {
        "Bookmark time zones must be UTC"
      }
    }

    /**
     * Create a bookmark ID from the given book ID, kind and location.
     */

    private fun createBookmarkID(
      book: BookID,
      kind: BookmarkKind,
      location: PlayerPosition
    ): BookmarkID {
      try {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val utf8 = Charset.forName("UTF-8")
        messageDigest.update(book.value().toByteArray(utf8))
        messageDigest.update(kind.motivationURI.toByteArray(utf8))
        messageDigest.update(location.chapter.toString().toByteArray(utf8))
        messageDigest.update(location.part.toString().toByteArray(utf8))
        messageDigest.update(location.startOffset.toString().toByteArray(utf8))
        messageDigest.update(location.currentOffset.toString().toByteArray(utf8))

        val digestResult = messageDigest.digest()
        val builder = StringBuilder(64)
        for (index in digestResult.indices) {
          val bb = digestResult[index]
          builder.append(String.format("%02x", bb))
        }

        return BookmarkID(builder.toString())
      } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException(e)
      }
    }

    companion object {

      fun create(
        opdsId: String,
        location: PlayerPosition,
        kind: BookmarkKind,
        time: DateTime,
        deviceID: String,
        duration: Long,
        uri: URI?
      ): AudiobookBookmark {
        return AudiobookBookmark(
          opdsId = opdsId,
          location = location,
          kind = kind,
          time = time.toDateTime(DateTimeZone.UTC),
          deviceID = deviceID,
          duration = duration,
          uri = uri
        )
      }
    }
  }
}
