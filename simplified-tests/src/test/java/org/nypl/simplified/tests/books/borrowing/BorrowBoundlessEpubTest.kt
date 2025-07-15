package org.nypl.simplified.tests.books.borrowing

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.librarysimplified.services.api.ServiceDirectoryType
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.nypl.drm.core.BoundlessFulfilledCMEPUB
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.drm.core.DRMTaskResult
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.BookDRMInformationHandleBoundless
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.internal.BorrowBoundless
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.books.formats.api.StandardFormatNames.boundlessLicenseFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericEPUBFiles
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.nypl.simplified.tests.mocking.MockBookDatabase
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntry
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.tests.mocking.MockBorrowContext
import org.nypl.simplified.tests.mocking.MockBundledContentResolver
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class BorrowBoundlessEpubTest {

  private lateinit var androidContentResolver: ContentResolver
  private lateinit var downloadsDirectory: File
  private lateinit var androidContext: Application
  private lateinit var account: AccountType
  private lateinit var accountId: AccountID
  private lateinit var accountProvider: AccountProvider
  private lateinit var bookDatabase: BookDatabaseType
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bundledContent: MockBundledContentResolver
  private lateinit var contentResolver: MockContentResolver
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var services: ServiceDirectoryType
  private lateinit var taskRecorder: TaskRecorderType
  private lateinit var webServer: MockWebServer

  private val logger = LoggerFactory.getLogger(BorrowBoundlessEpubTest::class.java)

  @TempDir
  @JvmField
  var tempDir: File? = null

  @BeforeEach
  fun testSetup(@TempDir directory: File) {
    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.androidContext =
      Mockito.mock(Application::class.java)
    this.androidContentResolver =
      Mockito.mock(ContentResolver::class.java)
    this.downloadsDirectory =
      directory

    this.taskRecorder =
      TaskRecorder.create()
    this.contentResolver =
      MockContentResolver()
    this.bundledContent =
      MockBundledContentResolver()
    this.bookRegistry =
      BookRegistry.create()

    this.account =
      Mockito.mock(AccountType::class.java)

    Mockito.`when`(this.account.loginState)
      .thenReturn(
        AccountLoginState.AccountLoggedIn(
          AccountAuthenticationCredentials.Basic(
            userName = AccountUsername("someone"),
            password = AccountPassword("not a password"),
            adobeCredentials = null,
            authenticationDescription = "Basic",
            annotationsURI = URI("https://www.example.com"),
            deviceRegistrationURI = URI("https://www.example.com")
          )
        )
      )

    this.accountProvider =
      MockAccountProviders.fakeProvider(
        "urn:uuid:ea9480d4-5479-4ef1-b1d1-84ccbedb680f",
        this.webServer.hostName,
        this.webServer.port
      )

    Mockito.`when`(this.account.provider)
      .thenReturn(this.accountProvider)

    val androidContext =
      Mockito.mock(Context::class.java)

    this.httpClient =
      LSHTTPClients()
        .create(
          context = androidContext,
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-tests",
            applicationVersion = "999.999.0",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    this.accountId =
      AccountID.generate()

    this.bookDatabase =
      MockBookDatabase(this.accountId)

    this.services = MutableServiceDirectory().apply {
      this.putService(
        OPDSFeedParserType::class.java,
        OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
      )
    }
  }

  private fun createContext(
    feedEntry: OPDSAcquisitionFeedEntry,
    acquisitionPath: OPDSAcquisitionPath,
    downloadedFile: File? = null
  ): MockBorrowContext {
    val book = Book(
      id = BookIDs.newFromOPDSEntry(feedEntry),
      account = this.accountId,
      cover = null,
      thumbnail = null,
      entry = feedEntry,
      formats = listOf()
    )

    val tempDirPath = this.tempDir!!.toPath()
    val bookDatabaseEntry = MockBookDatabaseEntry(book)

    bookDatabaseEntry.formatHandlesField.clear()

    bookDatabaseEntry.formatHandlesField.add(
      MockBookDatabaseEntryFormatHandleEPUB(
        book.id,
        Files.createTempDirectory(tempDirPath, "boundlessFormatHandleEPUB").toFile()
      ).apply {
        this.drmInformationHandleField = BookDRMInformationHandleBoundless(
          directory = Files.createTempDirectory(tempDirPath, "boundlessDRMInfoEPUB").toFile(),
          format = this.formatDefinition,
          onUpdate = {}
        )
      }
    )

    bookDatabaseEntry.formatHandlesField.add(
      MockBookDatabaseEntryFormatHandleAudioBook(
        book.id,
        Files.createTempDirectory(tempDirPath, "boundlessFormatHandleAudioBook").toFile()
      ).apply {
        this.drmInformationHandleField = BookDRMInformationHandleBoundless(
          directory = Files.createTempDirectory(tempDirPath, "boundlessDRMInfoAudioBook").toFile(),
          format = this.formatDefinition,
          onUpdate = {}
        )
      }
    )

    bookDatabaseEntry.formatHandlesField.add(
      MockBookDatabaseEntryFormatHandlePDF(book.id).apply {
        this.drmInformationHandleField = BookDRMInformationHandleBoundless(
          directory = Files.createTempDirectory(tempDirPath, "boundlessDRMInfoPDF").toFile(),
          format = this.formatDefinition,
          onUpdate = {}
        )
      }
    )

    val context = MockBorrowContext(
      application = this.androidContext,
      logger = this.logger,
      bookRegistry = this.bookRegistry,
      bundledContent = this.bundledContent,
      temporaryDirectory = TestDirectories.temporaryDirectory(),
      account = this.account,
      clock = { Instant.now() },
      httpClient = this.httpClient,
      taskRecorder = this.taskRecorder,
      isCancelled = false,
      bookDatabaseEntry = bookDatabaseEntry,
      bookInitial = book,
      contentResolver = this.contentResolver,
    )

    context.opdsAcquisitionPath = acquisitionPath
    context.currentAcquisitionPathElement = acquisitionPath.elements.first()
    context.services = this.services
    context.boundlessService = Mockito.mock(BoundlessServiceType::class.java)

    return context
  }

  @AfterEach
  fun tearDown() {
    this.webServer.close()
  }

  @Test
  fun epubDownload_succeeds() {
    val feedEntry = BorrowTestFeeds.opdsBoundlessFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = boundlessLicenseFiles,
          target = Link.LinkTemplated(this.webServer.url(licenseTargetPath).toString()),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    val epubFile =
      File(this.tempDir, "test.epub")
    val licenseFile =
      File(this.tempDir, "license.json")

    epubFile.writeText("EPUB!")
    licenseFile.writeText("License!")

    val context = this.createContext(feedEntry, acquisitionPath)
    Mockito.`when`(
      context.boundlessService!!
        .fulfillEPUB(any(), any(), any(), any(), any(), any(), any())
    )
      .thenReturn(
        DRMTaskResult.DRMTaskSuccess(
          mapOf(), listOf(), BoundlessFulfilledCMEPUB(
            epubFile,
            licenseFile
          )
        )
      )

    // Execute the task. It is expected to halt early.
    val task = BorrowBoundless.createSubtask()

    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskHaltedEarly::class.java) {
      task.execute(context)
    }

    // The downloaded file should now be in the book format, and the license should be present.
    val formatHandle = context.bookDatabaseEntry
      .findFormatHandleForContentType(genericEPUBFiles)!!

    val format = formatHandle.format!! as BookFormat.BookFormatEPUB
    assertEquals("EPUB!", format.file!!.readText())

    val drmInfo = formatHandle.drmInformationHandle as BookDRMInformationHandleBoundless
    assertEquals("License!", drmInfo.info.license!!.readText())
  }

  @Test
  fun download_fails_whenLoansFeedCanNotBeRetrieved() {
    val feedEntry = BorrowTestFeeds.opdsBoundlessFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = boundlessLicenseFiles,
          target = Link.LinkTemplated(this.webServer.url(licenseTargetPath).toString()),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(500)
        .setBody("nope")

    this.webServer.enqueue(loansFeedResponse)

    val context = this.createContext(feedEntry, acquisitionPath)

    /*
     * Set up the boundless DRM service. The download fails.
     */

    Mockito.`when`(
      context.boundlessService!!
        .fulfillEPUB(any(), any(), any(), any(), any(), any(), any())
    )
      .thenReturn(DRMTaskResult.DRMTaskFailure(mapOf(), listOf()))

    // Execute the task. It is expected to halt early.
    val task = BorrowBoundless.createSubtask()
    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskFailed::class.java) {
      task.execute(context)
    }

    assertEquals(0, this.webServer.requestCount)
  }

  @Test
  fun download_fails_whenDRMUnsupported() {
    val feedEntry = BorrowTestFeeds.opdsBoundlessFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = boundlessLicenseFiles,
          target = Link.LinkBasic(this.webServer.url(licenseTargetPath).toUri()),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(500)
        .setBody("nope")

    this.webServer.enqueue(loansFeedResponse)

    val context = this.createContext(feedEntry, acquisitionPath)
    context.boundlessService = null

    // Execute the task. It is expected to fail.
    val task = BorrowBoundless.createSubtask()
    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskFailed::class.java) {
      task.execute(context)
    }

    assertEquals(0, this.webServer.requestCount)
  }

  @Throws(IOException::class)
  private fun copyToTempFile(
    name: String
  ): File {
    val file = File.createTempFile(name, "", this.tempDir)

    this.logger.debug("copyToTempFile: {} -> {}", name, file)

    FileOutputStream(file).use { output ->
      BorrowBoundlessEpubTest::class.java.getResourceAsStream(name)!!.use { input ->
        val buffer = ByteArray(4096)

        while (true) {
          val r = input.read(buffer)

          if (r == -1) {
            break
          }

          output.write(buffer, 0, r)
        }

        return file
      }
    }
  }
}
