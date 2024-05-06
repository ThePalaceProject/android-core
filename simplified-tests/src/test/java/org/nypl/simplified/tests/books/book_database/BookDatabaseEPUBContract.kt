package org.nypl.simplified.tests.books.book_database

import android.app.Application
import com.io7m.jfunctional.Option
import one.irradia.mime.vanilla.MIMEParser
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmark20210828
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.api.bookmark.SerializedLocatorLegacyCFI
import org.nypl.simplified.books.book_database.BookDRMInformationHandleACS
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.BookDRMInformationHandleNone
import org.nypl.simplified.books.book_database.BookDatabase
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSJSONParser
import org.nypl.simplified.opds.core.OPDSJSONSerializer
import org.nypl.simplified.tests.books.BookFormatsTesting
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID

abstract class BookDatabaseEPUBContract {

  private val logger =
    LoggerFactory.getLogger(BookDatabaseEPUBContract::class.java)
  private val accountID =
    AccountID(UUID.fromString("46d17029-14ba-4e34-bcaa-def02713575a"))

  protected abstract fun context(): Application

  /**
   * Creating a book database entry for a feed that contains an EPUB acquisition results in an
   * EPUB format. Reopening the database shows that the data is preserved.
   *
   * @throws Exception On errors
   */

  @Test
  fun testEntryLastReadLocation() {
    val parser = OPDSJSONParser.newParser()
    val serializer = OPDSJSONSerializer.newSerializer()
    val directory = DirectoryUtilities.directoryCreateTemporary()
    val database0 = BookDatabase.open(context(), parser, serializer, BookFormatsTesting.supportsEverything, accountID, directory)

    val feedEntry: OPDSAcquisitionFeedEntry = this.acquisitionFeedEntryWithEPUB()
    val bookID = BookIDs.newFromText("abcd")
    val databaseEntry0 = database0.createOrUpdate(bookID, feedEntry)

    this.run {
      val formatHandle =
        databaseEntry0.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)

      assertTrue(formatHandle != null, "Format is present")
      formatHandle!!
      assertEquals(null, formatHandle.format.lastReadLocation)

      val bookmark =
        SerializedBookmark20210828(
          bookTitle = "Title",
          opdsId = "abcd",
          location = SerializedLocatorLegacyCFI(
            chapterProgression = 0.5,
            contentCFI = "xyz",
            idRef = "abc"
          ),
          time = DateTime.now(DateTimeZone.UTC),
          bookChapterProgress = 0.5,
          bookChapterTitle = "A title",
          kind = BookmarkKind.BookmarkLastReadLocation,
          bookProgress = 0.25,
          uri = null,
          deviceID = "3475fa24-25ca-4ddb-9d7b-762358d5f83a"
        )

      formatHandle.setLastReadLocation(bookmark)
      assertEquals(bookmark, formatHandle.format.lastReadLocation)

      formatHandle.setLastReadLocation(null)
      assertEquals(null, formatHandle.format.lastReadLocation)
    }
  }

  /**
   * Saving and restoring bookmarks works.
   *
   * @throws Exception On errors
   */

  @Test
  fun testBookmarks() {
    val parser = OPDSJSONParser.newParser()
    val serializer = OPDSJSONSerializer.newSerializer()
    val directory = DirectoryUtilities.directoryCreateTemporary()
    val database0 = BookDatabase.open(context(), parser, serializer, BookFormatsTesting.supportsEverything, accountID, directory)

    val feedEntry: OPDSAcquisitionFeedEntry = this.acquisitionFeedEntryWithEPUB()
    val bookID = BookIDs.newFromText("abcd")
    val databaseEntry0 = database0.createOrUpdate(bookID, feedEntry)

    val bookmark0 =
      SerializedBookmarks.createWithCurrentFormat(
        bookTitle = "Title",
        opdsId = "abcd",
        location = SerializedLocatorLegacyCFI(
          chapterProgression = 0.5,
          contentCFI = "xyz",
          idRef = "abc"
        ),
        time = DateTime.now(DateTimeZone.UTC),
        kind = BookmarkKind.BookmarkExplicit,
        bookChapterTitle = "A title",
        bookProgress = 0.25,
        bookChapterProgress = 0.5,
        uri = null,
        deviceID = "3475fa24-25ca-4ddb-9d7b-762358d5f83a"
      )

    val bookmark1 =
      SerializedBookmarks.createWithCurrentFormat(
        bookTitle = "Title",
        opdsId = "abcd",
        location = SerializedLocatorLegacyCFI(
          chapterProgression = 0.6,
          contentCFI = "xyz",
          idRef = "abc"
        ),
        time = DateTime.now(DateTimeZone.UTC),
        kind = BookmarkKind.BookmarkExplicit,
        bookChapterTitle = "A title",
        bookProgress = 0.25,
        bookChapterProgress = 0.6,
        uri = null,
        deviceID = "3475fa24-25ca-4ddb-9d7b-762358d5f83a"
      )

    val bookmark2 =
      SerializedBookmarks.createWithCurrentFormat(
        bookTitle = "Title",
        opdsId = "abcd",
        location = SerializedLocatorLegacyCFI(
          chapterProgression = 0.7,
          contentCFI = "xyz",
          idRef = "abc"
        ),
        time = DateTime.now(DateTimeZone.UTC),
        kind = BookmarkKind.BookmarkExplicit,
        bookChapterTitle = "A title",
        bookProgress = 0.25,
        bookChapterProgress = 0.7,
        uri = null,
        deviceID = "3475fa24-25ca-4ddb-9d7b-762358d5f83a"
      )

    val bookmarks0 = listOf(bookmark0)
    val bookmarks1 = listOf(bookmark0, bookmark1, bookmark2)

    this.run {
      val formatHandle =
        databaseEntry0.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)

      assertTrue(formatHandle != null, "Format is present")
      formatHandle!!
      assertEquals(listOf<SerializedBookmark>(), formatHandle.format.bookmarks)

      formatHandle.addBookmark(bookmark0)
      assertEquals(bookmarks0, formatHandle.format.bookmarks)

      formatHandle.addBookmark(bookmark1)
      formatHandle.addBookmark(bookmark2)
      assertEquals(bookmarks1, formatHandle.format.bookmarks)
    }

    val database1 =
      BookDatabase.open(context(), parser, serializer, BookFormatsTesting.supportsEverything, accountID, directory)
    val databaseEntry1 =
      database1.createOrUpdate(bookID, feedEntry)

    this.run {
      val formatHandle =
        databaseEntry1.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)

      assertTrue(formatHandle != null, "Format is present")

      formatHandle!!

      val expectedBookmarks = bookmarks1.sortedBy { b -> b.bookmarkId.value }
      val receivedBookmarks = formatHandle.format.bookmarks.sortedBy { b -> b.bookmarkId.value }

      assertEquals(expectedBookmarks[0], receivedBookmarks[0].withoutURI())
      assertEquals(expectedBookmarks[1], receivedBookmarks[1].withoutURI())
      assertEquals(expectedBookmarks[2], receivedBookmarks[2].withoutURI())
      assertEquals(expectedBookmarks.size, receivedBookmarks.size)
    }
  }

  /**
   * Setting and unsetting DRM works.
   *
   * @throws Exception On errors
   */

  @Test
  fun testEntryDRM() {
    val parser = OPDSJSONParser.newParser()
    val serializer = OPDSJSONSerializer.newSerializer()
    val directory = DirectoryUtilities.directoryCreateTemporary()
    val database0 = BookDatabase.open(context(), parser, serializer, BookFormatsTesting.supportsEverything, accountID, directory)

    val feedEntry: OPDSAcquisitionFeedEntry = this.acquisitionFeedEntryWithEPUB()
    val bookID = BookIDs.newFromText("abcd")
    val entry = database0.createOrUpdate(bookID, feedEntry)

    this.run {
      val formatHandle = entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)!!
      formatHandle.drmInformationHandle as BookDRMInformationHandleNone

      this.logger.debug("setting DRM to LCP")
      formatHandle.setDRMKind(BookDRMKind.LCP)
      formatHandle.drmInformationHandle as BookDRMInformationHandleLCP
      formatHandle.format.drmInformation as BookDRMInformation.LCP
      this.logger.debug("setting DRM to ACS")
      formatHandle.setDRMKind(BookDRMKind.ACS)
      formatHandle.drmInformationHandle as BookDRMInformationHandleACS
      formatHandle.format.drmInformation as BookDRMInformation.ACS
      this.logger.debug("setting DRM to NONE")
      formatHandle.setDRMKind(BookDRMKind.NONE)
      formatHandle.drmInformationHandle as BookDRMInformationHandleNone
      formatHandle.format.drmInformation as BookDRMInformation.None
    }
  }

  private fun acquisitionFeedEntryWithEPUB(): OPDSAcquisitionFeedEntry {
    val revoke = Option.none<URI>()
    val eb = OPDSAcquisitionFeedEntry.newBuilder(
      "abcd",
      "Title",
      DateTime.now(),
      OPDSAvailabilityOpenAccess.get(revoke)
    )

    eb.addAcquisition(
      OPDSAcquisition(
        OPDSAcquisition.Relation.ACQUISITION_BORROW,
        URI.create("http://example.com"),
        MIMEParser.parseRaisingException("application/epub+zip"),
        emptyList(),
        emptyMap()
      )
    )
    return eb.build()
  }
}
