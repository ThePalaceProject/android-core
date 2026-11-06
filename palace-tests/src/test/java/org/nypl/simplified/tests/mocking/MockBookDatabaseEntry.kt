package org.nypl.simplified.tests.mocking

import com.io7m.junreachable.UnimplementedCodeException
import one.irradia.mime.api.MIMECompatibility
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericEPUBFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericJsonAudioBook
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericPDFFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.lcpAudioBooks
import org.nypl.simplified.books.api.FeedBookIDConversions
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.opds12.OPDS12FeedConverter
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.feeds.api.FeedAcquisitionPaths
import org.slf4j.LoggerFactory
import java.io.File

class MockBookDatabaseEntry(
  private val booksDirectory: File,
  private val bookInitial: Book
) : BookDatabaseEntryType {

  private val logger =
    LoggerFactory.getLogger(MockBookDatabaseEntry::class.java)

  val formatHandlesField: MutableList<BookDatabaseEntryFormatHandle> =
    this.formatHandlesOf(this.bookInitial.entry).toMutableList()

  var entryWrites = 0
  var entryField: FeedEntryBook = this.bookInitial.entry
  var thumbnailField: File? = null
  var deleted = false
  var coverField: File? = null

  override val book: Book
    get() = this.makeBook()

  private fun makeBook(): Book {
    return Book(
      id = this.bookInitial.id,
      account = this.bookInitial.account,
      cover = this.coverField,
      thumbnail = this.thumbnailField,
      entry = this.entryField,
      formats = this.makeFormats()
    )
  }

  private fun makeFormats(): List<BookFormat> {
    return this.formatHandles.map { handle -> handle.format }
  }

  override fun setCover(file: File) {
    this.coverField = file
  }

  override fun setThumbnail(file: File) {
    this.thumbnailField = file
  }

  override fun writeOPDSEntry(opdsEntry: OPDSAcquisitionFeedEntry) {
    this.logger.debug("[{}]: writeOPDSEntry", this.bookInitial.id)

    val feedEntry = OPDS12FeedConverter.convertEntry(this.bookInitial.account, opdsEntry)
    this.addNecessaryHandles(feedEntry)
    this.entryField = feedEntry
    this.entryWrites++
  }

  private fun addNecessaryHandles(entry: FeedEntryBook) {
    val handles = this.formatHandlesOf(entry)
    for (handle in handles) {
      if (this.formatHandlesField.find { it.javaClass == handle.javaClass } == null) {
        this.logger.debug("adding handle {}", handle.javaClass.name)
        this.formatHandlesField.add(handle)
      }
    }
  }

  private fun formatHandlesOf(
    entry: FeedEntryBook
  ): List<BookDatabaseEntryFormatHandle> {
    val bookId = FeedBookIDConversions.toBookID(entry.bookID)
    val paths = FeedAcquisitionPaths.linearize(entry.acquisitions)
    val finalTypes = paths.map { it.asMIMETypes().last() }
    val formats = mutableListOf<BookDatabaseEntryFormatHandle>()
    for (finalType in finalTypes) {
      if (MIMECompatibility.isCompatibleStrictWithoutAttributes(finalType, genericEPUBFiles)) {
        formats.add(MockBookDatabaseEntryFormatHandleEPUB(bookId))
        continue
      }
      if (MIMECompatibility.isCompatibleStrictWithoutAttributes(finalType, genericPDFFiles)) {
        formats.add(MockBookDatabaseEntryFormatHandlePDF(bookId))
        continue
      }
      if (MIMECompatibility.isCompatibleStrictWithoutAttributes(finalType, lcpAudioBooks)) {
        formats.add(MockBookDatabaseEntryFormatHandleAudioBook(bookId, this.booksDirectory))
        continue
      }
      if (MIMECompatibility.isCompatibleStrictWithoutAttributes(finalType, genericJsonAudioBook)) {
        formats.add(MockBookDatabaseEntryFormatHandleAudioBook(bookId, this.booksDirectory))
        continue
      }
    }
    this.logger.debug("[{}]: creating {} format handles", this.bookInitial.id, formats.size)
    return formats.toList()
  }

  override fun delete() {
    this.deleted = true
  }

  override fun temporaryFile(): File {
    throw UnimplementedCodeException()
  }

  override val formatHandles: List<BookDatabaseEntryFormatHandle>
    get() = this.formatHandlesField
}
