package org.nypl.simplified.tests.books.borrowing

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.librarysimplified.services.api.ServiceDirectoryType
import org.mockito.Mockito
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
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowLCP
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericEPUBFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.lcpAudioBooks
import org.nypl.simplified.books.formats.api.StandardFormatNames.lcpLicenseFiles
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
import org.nypl.simplified.tests.mocking.MockLCPService
import org.readium.r2.lcp.LcpService
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class BorrowLCPTest {

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

  private val logger = LoggerFactory.getLogger(BorrowLCPTest::class.java)

  @TempDir
  @JvmField
  var tempDir: File? = null

  @BeforeEach
  fun testSetup() {
    this.webServer = MockWebServer()
    this.webServer.start(20000)

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
            annotationsURI = URI("https://www.example.com")
          )
        )
      )

    this.accountProvider =
      MockAccountProviders.fakeProvider(
        "urn:uuid:ea9480d4-5479-4ef1-b1d1-84ccbedb680f",
        webServer.hostName,
        webServer.port
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
      putService(
        OPDSFeedParserType::class.java,
        OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
      )
    }
  }

  private fun createContext(
    feedEntry: OPDSAcquisitionFeedEntry,
    acquisitionPath: OPDSAcquisitionPath,
    downloadedFile: File? = null
  ): BorrowContextType {
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
      MockBookDatabaseEntryFormatHandleEPUB(book.id).apply {
        drmInformationHandleField = BookDRMInformationHandleLCP(
          directory = Files.createTempDirectory(tempDirPath, "lcpDRMInfoEPUB").toFile(),
          format = formatDefinition,
          onUpdate = {}
        )
      }
    )

    bookDatabaseEntry.formatHandlesField.add(
      MockBookDatabaseEntryFormatHandleAudioBook(book.id).apply {
        drmInformationHandleField = BookDRMInformationHandleLCP(
          directory = Files.createTempDirectory(tempDirPath, "lcpDRMInfoAudioBook").toFile(),
          format = formatDefinition,
          onUpdate = {}
        )
      }
    )

    bookDatabaseEntry.formatHandlesField.add(
      MockBookDatabaseEntryFormatHandlePDF(book.id).apply {
        drmInformationHandleField = BookDRMInformationHandleLCP(
          directory = Files.createTempDirectory(tempDirPath, "lcpDRMInfoPDF").toFile(),
          format = formatDefinition,
          onUpdate = {}
        )
      }
    )

    val context = MockBorrowContext(
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
    context.lcpService = MockLCPService(
      publication = if (downloadedFile == null) {
        null
      } else {
        Mockito.mock(LcpService.AcquiredPublication::class.java).also {
          Mockito.`when`(it.localFile).thenReturn(downloadedFile)
        }
      }
    )

    return context
  }

  @AfterEach
  fun tearDown() {
    this.webServer.close()
  }

  @Test
  fun epubDownload_succeeds() {
    val feedEntry = BorrowTestFeeds.opdsLCPFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
      hashedPassphrase = ""
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = lcpLicenseFiles,
          target = webServer.url(licenseTargetPath).toUri(),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    // Expect a request for the loans feed to obtain the hashed passphrase for the book.

    val newHashedPassphrase = "b9e3323fb715306bd89fa311b4bc988cce0042fb1d9079d13f41acc10c6ef37a"

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/atom+xml;profile=opds-catalog;kind=acquisition")
        .setBody(
          """
          <feed xmlns="http://www.w3.org/2005/Atom" xmlns:schema="http://schema.org/" xmlns:lcp="http://readium.org/lcp-specs/ns">
            <id>https://example.com/library/loans/</id>
            <title>Loans</title>
            <updated>2022-03-10T00:53:24+00:00</updated>
            <entry schema:additionalType="http://schema.org/EBook">
              <title>Example EPUB</title>
              <id>${feedEntry.id}</id>
              <updated>2022-03-07T22:07:34+00:00</updated>
              <link type="application/vnd.readium.lcp.license.v1.0+json" rel="http://opds-spec.org/acquisition">
                <lcp:hashed_passphrase>$newHashedPassphrase</lcp:hashed_passphrase>
              </link>
            </entry>
          </feed>
          """
        )

    this.webServer.enqueue(loansFeedResponse)

    // Expect a request to the acquisition URL to get the LCP license.

    val licenseResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/vnd.readium.lcp.license.v1.0+json")
        .setBody("{}")

    this.webServer.enqueue(licenseResponse)

    // Expect the LcpService to be used to download the publication. Create a mock downloaded file.

    val downloadedFile = copyToTempFile("/org/nypl/simplified/tests/books/empty.epub")

    // Create a mock borrow context.

    val context = createContext(
      feedEntry,
      acquisitionPath,
      downloadedFile
    )

    // Execute the task. It is expected to halt early.

    val task = BorrowLCP.createSubtask()

    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskHaltedEarly::class.java) {
      task.execute(context)
    }

    // The first request should have been to the loans feed for the account.

    Assertions.assertEquals(2, this.webServer.requestCount)

    Assertions.assertEquals(
      this.account.provider.loansURI.toString().toHttpUrl(),
      this.webServer.takeRequest().requestUrl
    )

    // The next request should have been to the target of the first acquisition path element.

    Assertions.assertEquals(
      this.webServer.url(licenseTargetPath),
      this.webServer.takeRequest().requestUrl
    )

    // The downloaded file should now be in the book format.

    val formatHandle = context.bookDatabaseEntry
      .findFormatHandleForContentType(StandardFormatNames.genericEPUBFiles)!!

    val format = formatHandle.format!! as BookFormat.BookFormatEPUB

    Assertions.assertEquals(downloadedFile, format.file)

    // The DRM info should now contain the hashed passphrase that was just retrieved.

    val drmInfo = formatHandle.drmInformationHandle as BookDRMInformationHandleLCP

    Assertions.assertEquals(newHashedPassphrase, drmInfo.info.hashedPassphrase)
  }

  @Test
  fun audioBookDownload_succeeds() {
    val feedEntry = BorrowTestFeeds.opdsLCPFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/audiobook+lcp",
      id = "urn:isbn:123456789",
      hashedPassphrase = ""
    )

    val licenseTargetPath = "/library/works/23124/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = lcpLicenseFiles,
          target = webServer.url(licenseTargetPath).toUri(),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = lcpAudioBooks,
          target = null,
          properties = mapOf()
        )
      )
    )

    // Expect a request for the loans feed to obtain the hashed passphrase for the book.

    val newHashedPassphrase = "b9e3323fb715306bd89fa311b4bc988cce0042fb1d9079d13f41acc10c6ef37a"

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/atom+xml;profile=opds-catalog;kind=acquisition")
        .setBody(
          """
          <feed xmlns="http://www.w3.org/2005/Atom" xmlns:schema="http://schema.org/" xmlns:lcp="http://readium.org/lcp-specs/ns">
            <id>https://example.com/library/loans/</id>
            <title>Loans</title>
            <updated>2022-03-10T00:53:24+00:00</updated>
            <entry schema:additionalType="http://bib.schema.org/Audiobook">
              <title>Example Audio Book</title>
              <id>${feedEntry.id}</id>
              <updated>2022-03-07T22:07:34+00:00</updated>
              <link type="application/vnd.readium.lcp.license.v1.0+json" rel="http://opds-spec.org/acquisition">
                <lcp:hashed_passphrase>$newHashedPassphrase</lcp:hashed_passphrase>
              </link>
            </entry>
          </feed>
          """
        )

    this.webServer.enqueue(loansFeedResponse)

    // Expect a request to the acquisition URL to get the LCP license.

    val licenseResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/vnd.readium.lcp.license.v1.0+json")
        .setBody("{}")

    this.webServer.enqueue(licenseResponse)

    // Expect the LcpService to be used to download the publication. Create a mock downloaded file.

    val downloadedFile = copyToTempFile("/org/nypl/simplified/tests/books/empty.lcpa")

    // Create a mock borrow context.

    val context = createContext(
      feedEntry,
      acquisitionPath,
      downloadedFile
    )

    // Execute the task. It is expected to halt early.

    val task = BorrowLCP.createSubtask()

    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskHaltedEarly::class.java) {
      task.execute(context)
    }

    // The first request should have been to the loans feed for the account.

    Assertions.assertEquals(2, this.webServer.requestCount)

    Assertions.assertEquals(
      this.account.provider.loansURI.toString().toHttpUrl(),
      this.webServer.takeRequest().requestUrl
    )

    // The next request should have been to the target of the first acquisition path element.

    Assertions.assertEquals(
      this.webServer.url(licenseTargetPath),
      this.webServer.takeRequest().requestUrl
    )

    // The downloaded file should now be in the book format.

    val formatHandle = context.bookDatabaseEntry
      .findFormatHandleForContentType(StandardFormatNames.lcpAudioBooks)!!

    val format = formatHandle.format!! as BookFormat.BookFormatAudioBook

    Assertions.assertEquals(downloadedFile, format.file)

    // The DRM info should now contain the hashed passphrase that was just retrieved.

    val drmInfo = formatHandle.drmInformationHandle as BookDRMInformationHandleLCP

    Assertions.assertEquals(newHashedPassphrase, drmInfo.info.hashedPassphrase)
  }

  @Test
  fun download_fails_whenLoansFeedCanNotBeRetrieved() {
    val feedEntry = BorrowTestFeeds.opdsLCPFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
      hashedPassphrase = ""
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = lcpLicenseFiles,
          target = webServer.url(licenseTargetPath).toUri(),
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

    val context = createContext(
      feedEntry,
      acquisitionPath
    )

    // Execute the task. It is expected to halt early.

    val task = BorrowLCP.createSubtask()

    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskFailed::class.java) {
      task.execute(context)
    }

    // The first and only request should have been to the loans feed for the account.

    Assertions.assertEquals(1, this.webServer.requestCount)

    Assertions.assertEquals(
      this.account.provider.loansURI.toString().toHttpUrl(),
      this.webServer.takeRequest().requestUrl
    )
  }

  @Test
  fun download_fails_whenHashedPassphraseIsNotFoundInLoansFeed() {
    val feedEntry = BorrowTestFeeds.opdsLCPFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
      hashedPassphrase = ""
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = lcpLicenseFiles,
          target = webServer.url(licenseTargetPath).toUri(),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    // Expect a request for the loans feed to obtain the hashed passphrase for the book.

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/atom+xml;profile=opds-catalog;kind=acquisition")
        .setBody(
          """
          <feed xmlns="http://www.w3.org/2005/Atom" xmlns:schema="http://schema.org/" xmlns:lcp="http://readium.org/lcp-specs/ns">
            <id>https://example.com/library/loans/</id>
            <title>Loans</title>
            <updated>2022-03-10T00:53:24+00:00</updated>
            <entry schema:additionalType="http://schema.org/EBook">
              <title>Example EPUB</title>
              <id>${feedEntry.id}</id>
              <updated>2022-03-07T22:07:34+00:00</updated>
              <link type="application/vnd.readium.lcp.license.v1.0+json" rel="http://opds-spec.org/acquisition">
                <!-- oh no, there's no hashed passphrase here -->
              </link>
            </entry>
          </feed>
          """
        )

    this.webServer.enqueue(loansFeedResponse)

    val context = createContext(
      feedEntry,
      acquisitionPath,
    )

    val task = BorrowLCP.createSubtask()

    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskFailed::class.java) {
      task.execute(context)
    }

    // The first and only request should have been to the loans feed for the account.

    Assertions.assertEquals(1, this.webServer.requestCount)

    Assertions.assertEquals(
      this.account.provider.loansURI.toString().toHttpUrl(),
      this.webServer.takeRequest().requestUrl
    )
  }

  @Test
  fun download_fails_whenLicenseCanNotBeRetrieved() {
    val feedEntry = BorrowTestFeeds.opdsLCPFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
      hashedPassphrase = ""
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = lcpLicenseFiles,
          target = webServer.url(licenseTargetPath).toUri(),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    // Expect a request for the loans feed to obtain the hashed passphrase for the book.

    val newHashedPassphrase = "b9e3323fb715306bd89fa311b4bc988cce0042fb1d9079d13f41acc10c6ef37a"

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/atom+xml;profile=opds-catalog;kind=acquisition")
        .setBody(
          """
          <feed xmlns="http://www.w3.org/2005/Atom" xmlns:schema="http://schema.org/" xmlns:lcp="http://readium.org/lcp-specs/ns">
            <id>https://example.com/library/loans/</id>
            <title>Loans</title>
            <updated>2022-03-10T00:53:24+00:00</updated>
            <entry schema:additionalType="http://schema.org/EBook">
              <title>Example EPUB</title>
              <id>${feedEntry.id}</id>
              <updated>2022-03-07T22:07:34+00:00</updated>
              <link type="application/vnd.readium.lcp.license.v1.0+json" rel="http://opds-spec.org/acquisition">
                <lcp:hashed_passphrase>$newHashedPassphrase</lcp:hashed_passphrase>
              </link>
            </entry>
          </feed>
          """
        )

    this.webServer.enqueue(loansFeedResponse)

    // Expect a request to the acquisition URL to get the LCP license.

    val licenseResponse =
      MockResponse()
        .setResponseCode(500)
        .setBody("oh no")

    this.webServer.enqueue(licenseResponse)

    val context = createContext(
      feedEntry,
      acquisitionPath,
    )

    val task = BorrowLCP.createSubtask()

    Assertions.assertThrows(BorrowSubtaskException.BorrowSubtaskFailed::class.java) {
      task.execute(context)
    }

    // The first request should have been to the loans feed for the account.

    Assertions.assertEquals(2, this.webServer.requestCount)

    Assertions.assertEquals(
      this.account.provider.loansURI.toString().toHttpUrl(),
      this.webServer.takeRequest().requestUrl
    )

    // The next request should have been to the target of the first acquisition path element.

    Assertions.assertEquals(
      this.webServer.url(licenseTargetPath),
      this.webServer.takeRequest().requestUrl
    )
  }

  @Throws(IOException::class)
  private fun copyToTempFile(
    name: String
  ): File {
    val file = File.createTempFile(name, "", this.tempDir)

    logger.debug("copyToTempFile: {} -> {}", name, file)

    FileOutputStream(file).use { output ->
      BorrowLCPTest::class.java.getResourceAsStream(name)!!.use { input ->
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
