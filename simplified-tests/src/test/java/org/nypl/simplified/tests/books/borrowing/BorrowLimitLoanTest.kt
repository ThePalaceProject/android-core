package org.nypl.simplified.tests.books.borrowing

import android.content.Context
import io.reactivex.disposables.Disposable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.DateTime
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.ReachedLoanLimit
import org.nypl.simplified.books.book_registry.BookStatus.RequestingLoan
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.borrowing.internal.BorrowLoanCreate
import org.nypl.simplified.books.formats.api.StandardFormatNames.genericEPUBFiles
import org.nypl.simplified.books.formats.api.StandardFormatNames.opdsAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntry
import org.nypl.simplified.tests.mocking.MockBorrowContext
import org.nypl.simplified.tests.mocking.MockBundledContentResolver
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

class BorrowLimitLoanTest {

  private lateinit var account: AccountType
  private lateinit var accountId: AccountID
  private lateinit var bookDatabaseEntry: MockBookDatabaseEntry
  private lateinit var bookEvents: MutableList<BookStatusEvent>
  private lateinit var bookID: BookID
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bookStates: MutableList<BookStatus>
  private lateinit var bundledContent: MockBundledContentResolver
  private lateinit var contentResolver: MockContentResolver
  private lateinit var context: MockBorrowContext
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var taskRecorder: TaskRecorderType
  private lateinit var webServer: MockWebServer
  private var bookRegistrySub: Disposable? = null

  private val logger = LoggerFactory.getLogger(BorrowLimitLoanTest::class.java)

  @BeforeEach
  fun testSetup() {
    this.taskRecorder =
      TaskRecorder.create()
    this.contentResolver =
      MockContentResolver()
    this.bundledContent =
      MockBundledContentResolver()

    this.bookRegistry =
      BookRegistry.create()
    this.bookEvents =
      mutableListOf()
    this.bookStates =
      mutableListOf()
    this.bookRegistrySub =
      this.bookRegistry.bookEvents()
        .subscribe(this::recordBookEvent)

    this.bookID =
      BookIDs.newFromText("x")
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

    val bookInitial =
      Book(
        id = this.bookID,
        account = this.accountId,
        cover = null,
        thumbnail = null,
        entry = OPDSAcquisitionFeedEntry.newBuilder("x", "Title", DateTime.now(), OPDSAvailabilityLoanable.get()).build(),
        formats = listOf()
      )

    this.bookDatabaseEntry =
      MockBookDatabaseEntry(bookInitial)

    this.context =
      MockBorrowContext(
        logger = this.logger,
        bookRegistry = this.bookRegistry,
        bundledContent = this.bundledContent,
        temporaryDirectory = TestDirectories.temporaryDirectory(),
        account = this.account,
        clock = { Instant.now() },
        httpClient = this.httpClient,
        taskRecorder = this.taskRecorder,
        isCancelled = false,
        bookDatabaseEntry = this.bookDatabaseEntry,
        bookInitial = bookInitial,
        contentResolver = this.contentResolver
      )

    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(opdsAcquisitionFeedEntry, null, emptyMap())

    this.webServer = MockWebServer()
    this.webServer.start(20000)
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
    this.bookRegistrySub?.dispose()
    this.webServer.close()
  }

  /**
   * A loan can't be created twice.
   */

  @Test
  fun testReachedLoanLimit() {
    val task = BorrowLoanCreate.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(opdsAcquisitionFeedEntry, null, emptyMap())
    this.context.currentRemainingOPDSPathElements =
      listOf(OPDSAcquisitionPathElement(genericEPUBFiles, null, emptyMap()))

    val response =
      MockResponse()
        .setResponseCode(403)
        .setHeader("Content-Type", "application/api-problem+json")
        .setBody(
          """{
  "type": "http://librarysimplified.org/terms/problem/loan-limit-reached"
}
          """.trimIndent()
        )

    this.webServer.enqueue(response)

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: Exception) {
      this.logger.error("exception: ", e)
    }

    assertEquals(0, this.bookDatabaseEntry.entryWrites)

    assertEquals(RequestingLoan::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(ReachedLoanLimit::class.java, this.bookStates.removeAt(0).javaClass)
    assertEquals(0, this.bookStates.size)
  }
}
