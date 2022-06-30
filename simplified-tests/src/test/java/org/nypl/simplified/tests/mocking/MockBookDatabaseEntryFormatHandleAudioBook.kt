package org.nypl.simplified.tests.mocking

import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.formats.api.StandardFormatNames
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MockBookDatabaseEntryFormatHandleAudioBook(
  val bookID: BookID,
  val directory: File? = null,
) : BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook() {

  var bookData: String? = null
  private var bookFile: File? = null

  var formatField: BookFormat.BookFormatAudioBook =
    BookFormat.BookFormatAudioBook(
      drmInformation = BookDRMInformation.None,
      contentType = StandardFormatNames.genericAudioBooks.first(),
      file = null,
      manifest = null,
      lastReadLocation = null,
      bookmarks = listOf()
    )

  var drmInformationHandleField: BookDRMInformationHandle =
    object : BookDRMInformationHandle.NoneHandle() {
      override val info: BookDRMInformation.None =
        BookDRMInformation.None
    }

  override val format: BookFormat.BookFormatAudioBook
    get() = this.formatField

  override fun copyInManifestAndURI(data: ByteArray, manifestURI: URI) {
    this.formatField = this.formatField.copy(
      manifest = BookFormat.AudioBookManifestReference(
        manifestURI,
        File("whatever")
      )
    )
  }

  override fun copyInBook(file: File) {
    this.bookData = file.readText()
    this.bookFile = File(this.directory, "book.epub")

    Files.copy(file.toPath(), this.bookFile!!.toPath(), StandardCopyOption.REPLACE_EXISTING)

    this.formatField = this.formatField.copy(file = this.bookFile)
    check(this.formatField.isDownloaded)
  }

  override fun moveInBook(file: File) {
    this.bookData = file.readText()
    this.bookFile = File(this.directory, "book.zip")

    file.renameTo(this.bookFile)

    this.formatField = this.formatField.copy(file = this.bookFile)
    check(this.formatField.isDownloaded)
  }

  override fun setLastReadLocation(bookmark: Bookmark.AudiobookBookmark?) {
    this.formatField = this.formatField.copy(
      lastReadLocation = bookmark
    )
  }

  override fun setBookmarks(bookmarks: List<Bookmark.AudiobookBookmark>) {
    this.formatField = this.formatField.copy(
      bookmarks = bookmarks
    )
  }

  override val drmInformationHandle: BookDRMInformationHandle
    get() = this.drmInformationHandleField

  override fun setDRMKind(kind: BookDRMKind) {
  }

  override fun deleteBookData() {
    this.bookData = null
    this.bookFile = null
    this.formatField = this.formatField.copy(manifest = null)
  }
}
