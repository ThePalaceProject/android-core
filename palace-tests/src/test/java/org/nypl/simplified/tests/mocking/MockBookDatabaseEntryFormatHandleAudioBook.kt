package org.nypl.simplified.tests.mocking

import android.app.Application
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkID
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.formats.api.StandardFormatNames
import java.io.File
import java.net.URI
import java.nio.file.Files

class MockBookDatabaseEntryFormatHandleAudioBook(
  val bookID: BookID,
  val directory: File,
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

  override fun copyInManifestAndURI(data: ByteArray, manifestURI: URI?) {
    val file = File(this.directory,"manifest")
    Files.write(file.toPath(), data)
    this.formatField = this.formatField.copy(
      manifest = BookFormat.AudioBookManifestReference(manifestURI, file)
    )
  }

  override fun setLastReadLocation(bookmark: SerializedBookmark?) {
    this.formatField = this.formatField.copy(
      lastReadLocation = bookmark
    )
  }

  override fun addBookmark(bookmark: SerializedBookmark) {
    val newList = arrayListOf<SerializedBookmark>()
    newList.addAll(this.formatField.bookmarks)
    newList.add(bookmark)
    this.formatField = this.formatField.copy(bookmarks = newList.toList())
  }

  override fun deleteBookmark(bookmarkId: BookmarkID) {
    val newList = arrayListOf<SerializedBookmark>()
    newList.addAll(this.formatField.bookmarks)
    newList.removeIf { b -> b.bookmarkId == bookmarkId }
    this.formatField = this.formatField.copy(bookmarks = newList.toList())
  }

  override val drmInformationHandle: BookDRMInformationHandle
    get() = this.drmInformationHandleField

  override fun setDRMKind(kind: BookDRMKind) {
  }

  override fun deleteBookData(
    context: Application
  ) {
    this.bookData = null
    this.bookFile = null
    this.formatField = this.formatField.copy(manifest = null)
  }
}
