package org.nypl.simplified.books.book_database

import com.fasterxml.jackson.databind.ObjectMapper
import net.jcip.annotations.GuardedBy
import one.irradia.mime.api.MIMEType
import org.joda.time.DateTime
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkJSON
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.files.FileUtilities
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException
import java.lang.NumberFormatException

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
        drmInfo = this.drmInformationHandle.info
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

  override fun copyInBook(file: File) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileCopy(file, this.fileBook)
      this.formatRef = this.formatRef.copy(file = this.fileBook)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun setLastReadLocation(bookmark: Bookmark.PDFBookmark?) {
    val newFormat = synchronized(this.dataLock) {
      if (bookmark != null) {
        FileUtilities.fileWriteUTF8Atomically(
          this.fileLastRead,
          this.fileLastReadTmp,
          BookmarkJSON.serializePdfBookmarkToString(this.parameters.objectMapper, bookmark)
        )
      } else {
        FileUtilities.fileDelete(this.fileLastRead)
      }

      this.formatRef = this.formatRef.copy(lastReadLocation = bookmark)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun setBookmarks(bookmarks: List<Bookmark.PDFBookmark>) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileWriteUTF8Atomically(
        this.fileBookmarks,
        this.fileBookmarksTmp,
        BookmarkJSON.serializePdfBookmarksToString(this.parameters.objectMapper, bookmarks)
      )
      this.formatRef = this.formatRef.copy(bookmarks = bookmarks)
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
      drmInfo: BookDRMInformation
    ): BookFormat.BookFormatPDF {
      return BookFormat.BookFormatPDF(
        bookmarks = loadBookmarksIfPresent(objectMapper, fileBookmarks),
        file = if (fileBook.isFile) fileBook else null,
        lastReadLocation = loadLastReadLocationIfPresent(objectMapper, fileLastRead),
        contentType = contentType,
        drmInformation = drmInfo
      )
    }

    @Throws(IOException::class)
    private fun loadBookmarksIfPresent(
      objectMapper: ObjectMapper,
      fileBookmarks: File
    ): List<Bookmark.PDFBookmark> {
      return if (fileBookmarks.isFile) {
        loadBookmarks(
          objectMapper = objectMapper,
          fileBookmarks = fileBookmarks
        )
      } else {
        listOf()
      }
    }

    private fun loadBookmarks(
      objectMapper: ObjectMapper,
      fileBookmarks: File
    ): List<Bookmark.PDFBookmark> {
      val tree = objectMapper.readTree(fileBookmarks)
      val array = JSONParserUtilities.checkArray(null, tree)

      val bookmarks = arrayListOf<Bookmark.PDFBookmark>()

      array.forEach { node ->
        try {
          val bookmark = BookmarkJSON.deserializePdfBookmarkFromJSON(
            kind = BookmarkKind.BookmarkExplicit,
            node = node
          )
          bookmarks.add(bookmark)
        } catch (exception: JSONParseException) {
          this.logger.debug("There was an error parsing the pdf bookmark from bookmarks file")
        }
      }

      return bookmarks
    }

    @Throws(IOException::class)
    private fun loadLastReadLocation(
      objectMapper: ObjectMapper,
      fileLastRead: File
    ): Bookmark.PDFBookmark {
      val serialized = FileUtilities.fileReadUTF8(fileLastRead)
      return try {
        Bookmark.PDFBookmark.create(
          opdsId = "",
          kind = BookmarkKind.BookmarkLastReadLocation,
          time = DateTime.now(),
          pageNumber = serialized.toInt(),
          deviceID = "",
          uri = null
        )
      } catch (exception: NumberFormatException) {
        this.logger.debug("The stored bookmark is not from the older version")
        BookmarkJSON.deserializePdfBookmarkFromString(
          objectMapper = objectMapper,
          kind = BookmarkKind.BookmarkLastReadLocation,
          serialized = serialized
        )
      }
    }

    @Throws(IOException::class)
    private fun loadLastReadLocationIfPresent(
      objectMapper: ObjectMapper,
      fileLastRead: File
    ): Bookmark.PDFBookmark? {
      return if (fileLastRead.isFile) {
        loadLastReadLocation(objectMapper, fileLastRead)
      } else {
        null
      }
    }
  }
}
