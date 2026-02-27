package org.nypl.simplified.tests.books.borrowing

import android.app.Application
import io.reactivex.disposables.Disposable
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountCookie
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.Downloading
import org.nypl.simplified.books.book_registry.BookStatus.FailedDownload
import org.nypl.simplified.books.book_registry.BookStatus.Loaned
import org.nypl.simplified.books.book_registry.BookStatus.Loaned.LoanedDownloaded
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.books.borrowing.internal.BorrowAudiobookAuthorizationHandler
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpConnectionFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpContentTypeIncompatible
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpRequestFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.requiredURIMissing
import org.nypl.simplified.books.borrowing.internal.BorrowSAMLDownload
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericEPUBFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericPDFFiles
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.patron.api.PatronAuthorization
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.nypl.simplified.tests.mocking.MockBookDatabase
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntry
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.tests.mocking.MockBorrowContext
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class BorrowSAMLDownloadTest {

  private lateinit var account: AccountType
  private lateinit var accountId: AccountID
  private lateinit var accountProvider: AccountProvider
  private lateinit var bookDatabase: BookDatabaseType
  private lateinit var bookDatabaseEntry: MockBookDatabaseEntry
  private lateinit var bookEvents: MutableList<BookStatusEvent>
  private lateinit var bookFormatSupport: BookFormatSupportType
  private lateinit var bookID: BookID
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bookStates: MutableList<BookStatus>
  private lateinit var contentResolver: MockContentResolver
  private lateinit var context: MockBorrowContext
  private lateinit var epubHandle: MockBookDatabaseEntryFormatHandleEPUB
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var pdfHandle: MockBookDatabaseEntryFormatHandlePDF
  private lateinit var profile: ProfileType
  private lateinit var taskRecorder: TaskRecorderType
  private lateinit var webServer: MockWebServer
  private var bookRegistrySub: Disposable? = null
  private lateinit var authHandler: BorrowAudiobookAuthorizationHandler

  private val logger = LoggerFactory.getLogger(BorrowSAMLDownloadTest::class.java)

  private fun verifyBookRegistryHasStatus(clazz: Class<*>) {
    val registryStatus = this.bookRegistry.bookStatusOrNull(this.bookID)!!
    assertEquals(clazz, registryStatus.javaClass)
  }

  @BeforeEach
  fun testSetup(@TempDir bookDirectory: Path) {
    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.profile =
      Mockito.mock(ProfileType::class.java)
    this.taskRecorder =
      TaskRecorder.create()
    this.contentResolver =
      MockContentResolver()
    this.bookFormatSupport =
      Mockito.mock(BookFormatSupportType::class.java)
    this.bookRegistry =
      BookRegistry.create()
    this.bookStates =
      mutableListOf()
    this.bookEvents =
      mutableListOf()
    this.bookRegistrySub =
      this.bookRegistry.bookEvents()
        .subscribe(this::recordBookEvent)

    this.account =
      Mockito.mock(AccountType::class.java)

    this.authHandler =
      BorrowAudiobookAuthorizationHandler(this.account)

    Mockito.`when`(this.account.loginState)
      .thenReturn(
        AccountLoginState.AccountLoggedIn(
          AccountAuthenticationCredentials.SAML2_0(
            accessToken = "1234ABCD",
            patronInfo = "",
            cookies = listOf(
              AccountCookie(this.webServer.url("").toString(), "foo=bar; path=/"),
              AccountCookie("http://somehost.org", "another=value")
            ),
            adobeCredentials = null,
            authenticationDescription = "SAML",
            annotationsURI = URI("https://www.example.com"),
            deviceRegistrationURI = URI("https://www.example.com"),
            patronAuthorization = PatronAuthorization("identifier", null)
          )
        )
      )

    this.accountProvider =
      MockAccountProviders.fakeProvider("urn:uuid:ea9480d4-5479-4ef1-b1d1-84ccbedb680f")

    val androidContext =
      Mockito.mock(Application::class.java)

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

    val initialFeedEntry =
      BorrowTestFeeds.opdsLoanedFeedEntryOfType(this.webServer, genericEPUBFiles.fullType)
    this.bookID =
      BookIDs.newFromOPDSEntry(initialFeedEntry)

    val bookInitial =
      Book(
        id = this.bookID,
        account = this.accountId,
        cover = null,
        thumbnail = null,
        entry = initialFeedEntry,
        formats = listOf()
      )

    this.bookDatabase =
      MockBookDatabase(booksDirectory = bookDirectory.toFile(), owner = this.accountId)
    this.bookDatabaseEntry =
      MockBookDatabaseEntry(booksDirectory = bookDirectory.toFile(), bookInitial)
    this.pdfHandle =
      MockBookDatabaseEntryFormatHandlePDF(this.bookID)
    this.epubHandle =
      MockBookDatabaseEntryFormatHandleEPUB(this.bookID)

    this.context =
      MockBorrowContext(
        application = androidContext,
        audiobookAuthorizationHandler = this.authHandler,
        logger = this.logger,
        bookRegistry = this.bookRegistry,
        temporaryDirectory = TestDirectories.temporaryDirectory(),
        account = this.account,
        clock = { Instant.now() },
        httpClient = this.httpClient,
        taskRecorder = this.taskRecorder,
        isCancelled = false,
        bookDatabaseEntry = this.bookDatabaseEntry,
        bookInitial = bookInitial,
        contentResolver = this.contentResolver,
        profile = this.profile
      )
  }

  private fun recordBookEvent(event: BookStatusEvent) {
    this.logger.debug("event: {}", event)
    val status = event.statusNow!!
    this.logger.debug("status: {}", status)
    this.bookStates.add(status)
    this.bookEvents.add(event)
  }

  @AfterEach
  fun tearDown() {
    this.webServer.close()
  }

  /**
   * A SAML download can't be performed if no URI is available.
   */

  @Test
  fun testNoURI() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null, emptyMap())

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(requiredURIMissing, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A failing HTTP connection fails the download.
   */

  @Test
  fun testHTTPConnectionFails() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null, emptyMap())

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpConnectionFailed, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A 404 fails the download.
   */

  @Test
  fun testHTTP404Fails() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null, emptyMap())

    this.webServer.enqueue(MockResponse().setResponseCode(404))

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpRequestFailed, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * An incompatible non-HTML MIME type fails the download.
   */

  @Test
  fun testMIMEIncompatibleFails() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null, emptyMap())

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/plain")

    this.webServer.enqueue(response)

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpContentTypeIncompatible, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * An HTML MIME type pauses the download to wait for external authentication when no SAML download
   * context is present.
   */

  @Test
  fun testMIMEHTMLPausesDownload() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null, emptyMap())

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/html")

    this.webServer.enqueue(response)

    task.execute(this.context)

    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(
      BookStatus.DownloadWaitingForExternalAuthentication::class.java,
      this.bookStates.removeAt(0).javaClass
    )
    assertEquals(0, this.bookStates.size)
  }

  /**
   * An HTML MIME type fails the download when a SAML download context is present that indicates the
   * SAML authentication is complete for this download.
   */

  @Test
  fun testMIMEHTMLFailsWhenSAMLAuthComplete() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null, emptyMap())
    this.context.samlDownloadContext = SAMLDownloadContext(
      isSAMLAuthComplete = true,
      downloadURI = this.context.currentURICheck(),
      authCompleteDownloadURI = this.context.currentURICheck()
    )

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/html")

    this.webServer.enqueue(response)

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(httpContentTypeIncompatible, this.taskRecorder.finishFailure<Unit>().lastErrorCode)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(FailedDownload::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * A PDF file is downloaded.
   */

  @Test
  fun testDownloadOkPDF() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericPDFFiles, null, emptyMap())

    this.bookDatabaseEntry.writeOPDSEntry(
      BorrowTestFeeds.opdsLoanedFeedEntryOfType(this.webServer, genericPDFFiles.fullType)
    )
    this.bookDatabaseEntry.entryWrites = 0
    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.pdfHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/pdf")
        .setBody("PDF!")

    this.webServer.enqueue(response)

    task.execute(this.context)

    this.verifyBookRegistryHasStatus(LoanedDownloaded::class.java)
    assertEquals("PDF!", this.pdfHandle.bookData)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(LoanedDownloaded::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * An EPUB file is downloaded.
   */

  @Test
  fun testDownloadOkEPUB() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null, emptyMap())

    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.epubHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/epub+zip")
        .setBody("EPUB!")

    this.webServer.enqueue(response)

    task.execute(this.context)

    this.verifyBookRegistryHasStatus(LoanedDownloaded::class.java)
    assertEquals("EPUB!", this.epubHandle.bookData)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(LoanedDownloaded::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * When a SAML authentication is complete, the file is downloaded from the authenticated URL in
   * the SAML download context instead of the original URL.
   */

  @Test
  fun testDownloadUsesAuthenticatedURL() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/original/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null, emptyMap())
    this.context.samlDownloadContext = SAMLDownloadContext(
      isSAMLAuthComplete = true,
      downloadURI = this.context.currentURICheck(),
      authCompleteDownloadURI = this.webServer.url("/authenticated/book.epub").toUri()
    )

    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.epubHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    this.webServer.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse =
        if (request.requestUrl!!.pathSegments.contains("authenticated")) {
          MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/epub+zip")
            .setBody("EPUB!")
        } else {
          MockResponse()
            .setResponseCode(404)
            .setBody("Wrong!")
        }
    }

    task.execute(this.context)

    this.verifyBookRegistryHasStatus(LoanedDownloaded::class.java)
    assertEquals("EPUB!", this.epubHandle.bookData)
    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(Downloading::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(LoanedDownloaded::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }

  /**
   * The bearer token and cookies from the account credentials are sent with requests.
   */

  @Test
  fun testSendBearerTokenAndCookies() {
    val task = BorrowSAMLDownload.createSubtask()

    this.context.currentURIField =
      Link.LinkBasic(this.webServer.url("/book.epub").toUri())
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(genericEPUBFiles, null, emptyMap())

    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(this.epubHandle)
    check(this.bookDatabaseEntry.formatHandlesField.size == 1)
    check(BookStatus.fromBook(this.bookDatabaseEntry.book) is Loaned)

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/epub+zip")
        .setBody("EPUB!")

    this.webServer.enqueue(response)

    task.execute(this.context)

    val sent0 = this.webServer.takeRequest()
    assertEquals("Bearer 1234ABCD", sent0.getHeader("Authorization"))
    assertEquals("foo=bar;", sent0.getHeader("Cookie"))
  }
}
