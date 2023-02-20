package org.nypl.simplified.tests.books.preview

import android.content.Context
import io.reactivex.disposables.Disposable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookPreviewRegistry
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.books.preview.BookPreviewErrorCodes
import org.nypl.simplified.books.preview.BookPreviewRequirements
import org.nypl.simplified.books.preview.BookPreviewTask
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.TestDirectories
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class BookPreviewTaskTest {

  private lateinit var bookPreviewStatus: ArrayList<BookPreviewStatus>
  private lateinit var bookPreviewRegistry: BookPreviewRegistryType
  private lateinit var disposable: Disposable
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var opdsFeedEntryNoPreviews: OPDSAcquisitionFeedEntry
  private lateinit var opdsFeedEntryPreviewEPUB: OPDSAcquisitionFeedEntry
  private lateinit var opdsFeedEntryPreviewPDF: OPDSAcquisitionFeedEntry
  private lateinit var opdsFeedEntryPreviewWMA: OPDSAcquisitionFeedEntry
  private lateinit var opdsFeedEntryTwoPreviews: OPDSAcquisitionFeedEntry
  private lateinit var temporaryDirectory: File
  private lateinit var webServer: MockWebServer

  private val logger = LoggerFactory.getLogger(BookPreviewTaskTest::class.java)

  @BeforeEach
  fun testSetup() {
    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.opdsFeedEntryNoPreviews =
      BookPreviewTestUtils.opdsFeedEntryNoAcquisitionsOfType(
        this.webServer,
        StandardFormatNames.genericEPUBFiles.fullType
      )

    this.opdsFeedEntryPreviewEPUB =
      BookPreviewTestUtils.opdsFeedEntryOfType(
        this.webServer,
        StandardFormatNames.genericEPUBFiles.fullType
      )

    this.opdsFeedEntryPreviewPDF =
      BookPreviewTestUtils.opdsFeedEntryOfType(
        this.webServer,
        StandardFormatNames.genericPDFFiles.fullType
      )

    this.opdsFeedEntryPreviewWMA =
      BookPreviewTestUtils.opdsFeedEntryOfType(
        this.webServer,
        StandardFormatNames.wmaAudioBooks.fullType
      )

    this.opdsFeedEntryTwoPreviews =
      BookPreviewTestUtils.opdsFeedEntryOfTypes(
        this.webServer,
        StandardFormatNames.textHtmlBook.fullType,
        StandardFormatNames.genericEPUBFiles.fullType
      )

    this.temporaryDirectory = TestDirectories.temporaryDirectory()
    this.bookPreviewRegistry = BookPreviewRegistry(temporaryDirectory)

    this.bookPreviewStatus = arrayListOf()
    this.disposable = this.bookPreviewRegistry.observeBookPreviewStatus().subscribe {
      bookPreviewStatus.add(it)
    }

    val context =
      Mockito.mock(Context::class.java)

    this.httpClient =
      LSHTTPClients()
        .create(
          context = context,
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-tests",
            applicationVersion = "999.999.0",
            tlsOverrides = null,
            timeout = Pair(3L, TimeUnit.SECONDS)
          )
        )
  }

  @AfterEach
  fun tearDown() {
    this.webServer.close()
    this.disposable.dispose()
  }

  @Test
  fun testAvailablePreviewAcquisitions() {
    val task = createTask(
      entry = this.opdsFeedEntryTwoPreviews,
      format = BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
    )

    executeAssumingSuccess(task)

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Ready.Embedded::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(0, this.bookPreviewStatus.size)
  }

  @Test
  fun testAvailableEpubPreviewAcquisition() {
    val task = createTask(
      entry = this.opdsFeedEntryPreviewEPUB,
      format = BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("Success")
    )

    executeAssumingSuccess(task)

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )
    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )
    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Ready.BookPreview::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(0, this.bookPreviewStatus.size)
  }

  @Test
  fun testAvailableWmaPreviewAcquisition() {
    val task = createTask(
      entry = this.opdsFeedEntryPreviewWMA,
      format = BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("Success")
    )

    executeAssumingSuccess(task)

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )
    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )
    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Ready.AudiobookPreview::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(0, this.bookPreviewStatus.size)
  }

  @Test
  fun testErrorDownloading() {
    val task = createTask(
      entry = this.opdsFeedEntryPreviewEPUB,
      format = BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setBody("Error")
    )

    executeAssumingFailure(task)

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.Downloading::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(
      BookPreviewStatus.HasPreview.DownloadFailed::class.java,
      this.bookPreviewStatus.removeAt(0).javaClass
    )

    Assertions.assertEquals(0, this.bookPreviewStatus.size)
  }

  @Test
  fun testNonSupportedBookFormat() {

    val task = createTask(
      entry = this.opdsFeedEntryPreviewPDF,
      format = BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF
    )

    val result = executeAssumingFailure(task)

    Assertions.assertEquals(
      BookPreviewErrorCodes.noSupportedPreviewAcquisitions,
      result.lastErrorCode
    )
  }

  @Test
  fun testNoAvailablePreviewAcquisitions() {
    val task = createTask(
      entry = this.opdsFeedEntryNoPreviews,
      format = BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
    )

    val result = executeAssumingFailure(task)

    Assertions.assertEquals(BookPreviewErrorCodes.noPreviewAcquisitions, result.lastErrorCode)

    Assertions.assertEquals(1, bookPreviewStatus.size)
    Assertions.assertEquals(bookPreviewStatus.first()::class.java, BookPreviewStatus.None.javaClass)
  }

  private fun createTask(
    entry: OPDSAcquisitionFeedEntry,
    format: BookFormats.BookFormatDefinition
  ): BookPreviewTask {
    return BookPreviewTask(
      bookPreviewRegistry = bookPreviewRegistry,
      bookPreviewRequirements = BookPreviewRequirements(
        clock = { Instant.now() },
        httpClient = this.httpClient,
        temporaryDirectory = this.temporaryDirectory
      ),
      feedEntry = entry,
      format = format
    )
  }

  private fun executeAssumingFailure(task: BookPreviewTask): TaskResult.Failure<*> {
    val result = task.execute()
    result.steps.forEach { this.logger.debug("{}", it) }
    return result as TaskResult.Failure
  }

  private fun executeAssumingSuccess(task: BookPreviewTask): TaskResult.Success<*> {
    val result = task.execute()
    result.steps.forEach { this.logger.debug("{}", it) }
    return result as TaskResult.Success
  }
}
