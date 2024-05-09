package org.nypl.simplified.books.book_database

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Preconditions
import net.jcip.annotations.GuardedBy
import one.irradia.mime.api.MIMEType
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.bookmark.BookmarkID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmarkFallbackValues
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.files.FileUtilities
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONSerializerUtilities
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Operations on PDF formats in database entries.
 */

internal class DatabaseFormatHandlePDF internal constructor(
  private val parameters: DatabaseFormatHandleParameters
) : BookDatabaseEntryFormatHandlePDF() {

  private val fileBook: File =
    File(this.parameters.directory, "pdf-book.pdf")
  private val fileLastRead: File =
    File(this.parameters.directory, "pdf-meta_last_read.json")
  private val fileLastReadTmp: File =
    File(this.parameters.directory, "pdf-meta_last_read.json.tmp")
  private val fileBookmarks: File =
    File(this.parameters.directory, "pdf-meta_bookmarks.json")
  private val fileBookmarksTmp: File =
    File(this.parameters.directory, "pdf-meta_bookmarks.json.tmp")

  private val dataLock: Any = Any()

  @GuardedBy("dataLock")
  private var drmHandleRef: BookDRMInformationHandle

  init {
    val drmHandleInitial =
      BookDRMInformationHandles.open(
        directory = this.parameters.directory,
        format = this.formatDefinition,
        bookFormats = this.parameters.bookFormatSupport,
        onUpdate = this::onDRMUpdated
      )

    if (drmHandleInitial == null) {
      try {
        FileUtilities.fileDelete(this.fileBook)
      } catch (e: Exception) {
        // Not much we can do about this.
      }

      val drmHandleNext =
        BookDRMInformationHandles.open(
          directory = this.parameters.directory,
          format = this.formatDefinition,
          bookFormats = this.parameters.bookFormatSupport,
          onUpdate = this::onDRMUpdated
        ) ?: throw IllegalStateException("Still could not open a DRM handle!")

      this.drmHandleRef = drmHandleNext
    } else {
      this.drmHandleRef = drmHandleInitial
    }
  }

  @GuardedBy("dataLock")
  private var formatRef: BookFormat.BookFormatPDF =
    synchronized(this.dataLock) {
      loadInitial(
        objectMapper = this.parameters.objectMapper,
        fileBook = this.fileBook,
        fileBookmarks = this.fileBookmarks,
        fileLastRead = this.fileLastRead,
        contentType = this.parameters.contentType,
        drmInfo = this.drmInformationHandle.info,
        bookmarkFallbackValues = SerializedBookmarkFallbackValues(
          kind = BookmarkKind.BookmarkExplicit,
          bookOPDSId = this.parameters.entry.book.entry.id,
          bookTitle = this.parameters.entry.book.entry.title
        )
      )
    }

  private fun onDRMUpdated() {
    this.parameters.onUpdated.invoke(this.refreshDRM())
  }

  private fun refreshDRM(): BookFormat.BookFormatPDF {
    return synchronized(this.dataLock) {
      this.formatRef = this.formatRef.copy(drmInformation = this.drmInformationHandle.info)
      this.formatRef
    }
  }

  override val format: BookFormat.BookFormatPDF
    get() = synchronized(this.dataLock, this::formatRef)

  override val drmInformationHandle: BookDRMInformationHandle
    get() = synchronized(this.dataLock, this::drmHandleRef)

  override fun setDRMKind(kind: BookDRMKind) {
    synchronized(this.dataLock) {
      val oldRef = (this.drmHandleRef as BookDRMInformationHandleBase)
      this.drmHandleRef = BookDRMInformationHandles.create(
        directory = this.parameters.directory,
        format = this.formatDefinition,
        drmKind = kind,
        onUpdate = this::onDRMUpdated
      )
      oldRef.close()
      this.onDRMUpdated()
    }
  }

  override fun deleteBookData() {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileDelete(this.fileBook)
      this.formatRef = this.formatRef.copy(file = null)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun deleteBookmark(bookmarkId: BookmarkID) {
    val newFormat = synchronized(this.dataLock) {
      val serialized = this.formatRef.bookmarks.filter {
          bookmark -> bookmark.bookmarkId != bookmarkId
      }

      FileUtilities.fileWriteUTF8Atomically(
        this.fileBookmarks,
        this.fileBookmarksTmp,
        JSONSerializerUtilities.serializeToString(
          serialized.map { x -> x.toJSON(this.parameters.objectMapper) }
        )
      )
      this.formatRef = this.formatRef.copy(bookmarks = serialized)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun copyInBook(file: File) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileCopy(file, this.fileBook)
      this.formatRef = this.formatRef.copy(file = this.fileBook)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun setLastReadLocation(bookmark: SerializedBookmark?) {
    if (bookmark != null) {
      Preconditions.checkArgument(
        bookmark.kind == BookmarkKind.BookmarkLastReadLocation,
        "Must use a last-read location bookmark"
      )
    }

    val newFormat = synchronized(this.dataLock) {
      if (bookmark != null) {
        FileUtilities.fileWriteUTF8Atomically(
          this.fileLastRead,
          this.fileLastReadTmp,
          JSONSerializerUtilities.serializeToString(bookmark.toJSON(this.parameters.objectMapper))
        )
      } else {
        FileUtilities.fileDelete(this.fileLastRead)
      }

      this.formatRef = this.formatRef.copy(lastReadLocation = bookmark)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun addBookmark(
    bookmark: SerializedBookmark
  ) {
    Preconditions.checkArgument(
      bookmark.kind == BookmarkKind.BookmarkExplicit,
      "Must use an explicit bookmark"
    )

    val newFormat = synchronized(this.dataLock) {
      val newBookmarks = arrayListOf<SerializedBookmark>()
      newBookmarks.addAll(this.formatRef.bookmarks)
      newBookmarks.removeIf { b -> b.bookmarkId == bookmark.bookmarkId }
      newBookmarks.removeIf { b -> b.kind == BookmarkKind.BookmarkLastReadLocation }
      newBookmarks.add(bookmark)

      FileUtilities.fileWriteUTF8Atomically(
        this.fileBookmarks,
        this.fileBookmarksTmp,
        JSONSerializerUtilities.serializeToString(
          newBookmarks.map { x -> x.toJSON(this.parameters.objectMapper) }
        )
      )
      this.formatRef = this.formatRef.copy(bookmarks = newBookmarks)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  companion object {

    private val logger =
      LoggerFactory.getLogger(DatabaseFormatHandlePDF::class.java)

    @Throws(IOException::class)
    private fun loadInitial(
      objectMapper: ObjectMapper,
      fileBook: File,
      fileBookmarks: File,
      fileLastRead: File,
      contentType: MIMEType,
      drmInfo: BookDRMInformation,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): BookFormat.BookFormatPDF {
      return BookFormat.BookFormatPDF(
        bookmarks = loadBookmarksIfPresent(objectMapper, fileBookmarks, bookmarkFallbackValues),
        file = if (fileBook.isFile) fileBook else null,
        lastReadLocation = loadLastReadLocationIfPresent(fileLastRead, bookmarkFallbackValues),
        contentType = contentType,
        drmInformation = drmInfo
      )
    }

    @Throws(IOException::class)
    private fun loadBookmarksIfPresent(
      objectMapper: ObjectMapper,
      fileBookmarks: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): List<SerializedBookmark> {
      return if (fileBookmarks.isFile) {
        loadBookmarks(
          objectMapper = objectMapper,
          fileBookmarks = fileBookmarks,
          bookmarkFallbackValues = bookmarkFallbackValues
        )
      } else {
        listOf()
      }
    }

    private fun loadBookmarks(
      objectMapper: ObjectMapper,
      fileBookmarks: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): List<SerializedBookmark> {
      val tree =
        objectMapper.readTree(fileBookmarks)
      val array =
        JSONParserUtilities.checkArray(null, tree)
      val bookmarks =
        arrayListOf<SerializedBookmark>()

      array.forEach { node ->
        try {
          val fallbackValues =
            bookmarkFallbackValues.copy(kind = BookmarkKind.BookmarkExplicit)
          bookmarks.add(SerializedBookmarks.parseBookmark(node, fallbackValues))
        } catch (exception: JSONParseException) {
          this.logger.debug("Failed to parse bookmark: ", exception)
        }
      }
      return bookmarks
    }

    @Throws(IOException::class)
    private fun loadLastReadLocation(
      fileLastRead: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): SerializedBookmark? {
      return try {
        val fallbackValues =
          bookmarkFallbackValues.copy(kind = BookmarkKind.BookmarkLastReadLocation)
        return SerializedBookmarks.parseBookmarkFromString(
          text = FileUtilities.fileReadUTF8(fileLastRead),
          fallbackValues = fallbackValues,
        )
      } catch (e: Exception) {
        this.logger.error("Failed to read the last-read location: ", e)
        null
      }
    }

    @Throws(IOException::class)
    private fun loadLastReadLocationIfPresent(
      fileLastRead: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): SerializedBookmark? {
      return if (fileLastRead.isFile) {
        loadLastReadLocation(fileLastRead, bookmarkFallbackValues)
      } else {
        null
      }
    }
  }
}
