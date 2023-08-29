package org.nypl.simplified.tests.http.refresh_token.bookmarks

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestConstants
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationTokenInfo
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationBodyNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationSelectorNode
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationTargetNode
import org.nypl.simplified.bookmarks.internal.BHTTPCalls
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.books.borrowing.BorrowTestFeeds
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockBookDatabase
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntry
import org.nypl.simplified.tests.mocking.MockBorrowContext
import org.nypl.simplified.tests.mocking.MockBundledContentResolver
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

class BookmarkRefreshTokenTest {

  private lateinit var account: MockAccount
  private lateinit var accountID: AccountID
  private lateinit var bookDatabase: BookDatabaseType
  private lateinit var bookDatabaseEntry: MockBookDatabaseEntry
  private lateinit var bookEvents: MutableList<BookStatusEvent>
  private lateinit var bookFormatSupport: BookFormatSupportType
  private lateinit var bookID: BookID
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bookStates: MutableList<BookStatus>
  private lateinit var bundledContent: MockBundledContentResolver
  private lateinit var calls: BHTTPCalls
  private lateinit var contentResolver: MockContentResolver
  private lateinit var context: MockBorrowContext
  private lateinit var http: LSHTTPClientType
  private lateinit var services: MutableServiceDirectory
  private lateinit var taskRecorder: TaskRecorderType
  private lateinit var webServer: MockWebServer

  private val logger = LoggerFactory.getLogger(BookmarkRefreshTokenTest::class.java)

  @BeforeEach
  fun testSetup() {
    this.accountID =
      AccountID.generate()
    this.account = MockAccount(this.accountID)

    val credentials = AccountAuthenticationCredentials.BasicToken(
      userName = AccountUsername("1234"),
      password = AccountPassword("5678"),
      authenticationTokenInfo = AccountAuthenticationTokenInfo(
        accessToken = "abcd",
        authURI = URI("https://www.authrefresh.com")
      ),
      adobeCredentials = null,
      authenticationDescription = null,
      annotationsURI = URI("https://www.example.com")
    )

    this.account.setLoginState(AccountLoginState.AccountLoggedIn(credentials))

    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.taskRecorder =
      TaskRecorder.create()
    this.contentResolver =
      MockContentResolver()
    this.bundledContent =
      MockBundledContentResolver()
    this.bookFormatSupport =
      Mockito.mock(BookFormatSupportType::class.java)
    this.bookRegistry =
      BookRegistry.create()
    this.bookStates =
      mutableListOf()
    this.bookEvents =
      mutableListOf()
    this.services = MutableServiceDirectory()

    val androidContext =
      Mockito.mock(Context::class.java)

    this.http =
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

    this.calls = BHTTPCalls(ObjectMapper(), this.http)

    val initialFeedEntry =
      BorrowTestFeeds.opdsLoanedFeedEntryOfType(
        this.webServer,
        StandardFormatNames.genericEPUBFiles.fullType
      )
    this.bookID =
      BookIDs.newFromOPDSEntry(initialFeedEntry)

    val bookInitial =
      Book(
        id = this.bookID,
        account = this.accountID,
        cover = null,
        thumbnail = null,
        entry = initialFeedEntry,
        formats = listOf()
      )

    this.bookDatabase =
      MockBookDatabase(this.accountID)
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
        httpClient = this.http,
        taskRecorder = this.taskRecorder,
        isCancelled = false,
        bookDatabaseEntry = this.bookDatabaseEntry,
        bookInitial = bookInitial,
        contentResolver = this.contentResolver
      )

    this.context.services = this.services
  }

  @AfterEach
  fun tearDown() {
    this.webServer.close()
  }

  @Test
  fun testAddBookmarkUpdateToken() {
    val credentials = account.loginState.credentials

    val targetURI =
      this.webServer.url("annotations").toUri()

    Assertions.assertNotNull(credentials)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody("")
    )

    calls.bookmarkAdd(
      account, targetURI, credentials!!,
      BookmarkAnnotation(
        context = "",
        body = BookmarkAnnotationBodyNode("", "", "", 0f),
        id = "id",
        type = "type",
        motivation = BookmarkKind.BookmarkLastReadLocation.motivationURI,
        target = BookmarkAnnotationTargetNode(
          source = "",
          selector = BookmarkAnnotationSelectorNode(
            type = "",
            value = ""
          )
        )
      )
    )

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  @Test
  fun testGetBookmarksUpdateToken() {
    val credentials = account.loginState.credentials

    val targetURI =
      this.webServer.url("annotations").toUri()

    Assertions.assertNotNull(credentials)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(
          "{" +
            "\"@context\": [\"http://www.w3.org/ns/anno.jsonld\", \"http://www.w3.org/ns/ldp.jsonld\"],\n" +
            "\"total\": 0,\n" +
            "\"type\": [\"BasicContainer\", \"AnnotationCollection\"],\n" +
            "\"id\": \"https://example.com/annotations/\",\n" +
            "\"first\": {\n" +
            "\"items\": [],\n" +
            "\"type\": \"AnnotationPage\",\n" +
            " \"id\": \"https://example.com/annotations/\"\n" +
            "}" +
            "}"
        )
    )

    calls.bookmarksGet(account, targetURI, credentials!!)

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  @Test
  fun testDeleteBookmarkUpdateToken() {
    val credentials = account.loginState.credentials

    val targetURI =
      this.webServer.url("annotations").toUri()

    Assertions.assertNotNull(credentials)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody("")
    )

    calls.bookmarkDelete(account, targetURI, credentials!!)

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  @Test
  fun testBookmarkSyncEnableUpdateToken() {
    val credentials = account.loginState.credentials

    val targetURI =
      this.webServer.url("annotations").toUri()

    Assertions.assertNotNull(credentials)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody("")
    )

    calls.syncingEnable(account, targetURI, credentials!!, true)

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  @Test
  fun testBookmarkSyncingIsEnabledUpdateToken() {
    val credentials = account.loginState.credentials

    val targetURI =
      this.webServer.url("annotations").toUri()

    Assertions.assertNotNull(credentials)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(
          """
        {
          "settings": {
            "simplified:synchronize_annotations": true
          }
        }
      """
        )
    )

    calls.syncingIsEnabled(account, targetURI, credentials!!)

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }
}
