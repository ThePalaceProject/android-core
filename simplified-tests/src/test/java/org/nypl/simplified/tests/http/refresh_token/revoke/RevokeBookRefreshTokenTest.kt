package org.nypl.simplified.tests.http.refresh_token.revoke

import android.content.Context
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jfunctional.Option
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import one.irradia.mime.vanilla.MIMEParser
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestConstants
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.drm.core.AdobeDeviceID
import org.nypl.drm.core.AdobeUserID
import org.nypl.drm.core.AdobeVendorID
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePostActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationTokenInfo
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.books.controller.BookRevokeTask
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.books.controller.TaskDumps
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntry
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.nypl.simplified.tests.mocking.MockRevokeStringResources
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RevokeBookRefreshTokenTest {
  private lateinit var account: MockAccount
  private lateinit var accountID: AccountID
  private lateinit var bookFormatSupport: BookFormatSupportType
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bundledContent: BundledContentResolverType
  private lateinit var contentResolver: MockContentResolver
  private lateinit var executorFeeds: ListeningExecutorService
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var webServer: MockWebServer

  private val profileID =
    ProfileID(UUID.fromString("06fa7899-658a-4480-a796-ebf2ff00d5ec"))

  private val logger = LoggerFactory.getLogger(RevokeBookRefreshTokenTest::class.java)

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
      adobeCredentials = AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken(
          userName = "user",
          password = "password",
          rawToken = "b85e7fd7-cf6e-4e39-8da6-8df8c9ee9779"
        ),
        deviceManagerURI = URI.create("http://www.example.com"),
        postActivationCredentials = AccountAuthenticationAdobePostActivationCredentials(
          deviceID = AdobeDeviceID("ca887d21-a56c-4314-811e-952d885d2115"),
          userID = AdobeUserID("19b25c06-8b39-4643-8813-5980bee45651")
        )
      ),
      authenticationDescription = "BasicToken",
      annotationsURI = URI("https://www.example.com")
    )

    this.account.setLoginState(AccountLoginState.AccountLoggedIn(credentials))
    this.bookRegistry =
      BookRegistry.create()

    this.bundledContent =
      BundledContentResolverType { uri -> throw FileNotFoundException("missing") }
    this.bookFormatSupport = Mockito.mock(BookFormatSupportType::class.java)
    this.contentResolver =
      MockContentResolver()
    this.executorFeeds = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.webServer = MockWebServer()
    this.webServer.start(20000)

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
  }

  @AfterEach
  fun tearDown() {
    this.executorFeeds.shutdown()
    this.webServer.close()
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testRevokeBookUpdateToken() {
    val profile =
      Mockito.mock(ProfileType::class.java)
    val profilesDatabase =
      Mockito.mock(ProfilesDatabaseType::class.java)
    val bookDatabase =
      Mockito.mock(BookDatabaseType::class.java)
    val bookId =
      BookID.create("a")

    val acquisition =
      OPDSAcquisition(
        OPDSAcquisition.Relation.ACQUISITION_BORROW,
        URI.create("http://www.example.com/0.feed"),
        MIMEParser.parseRaisingException("application/epub+zip"),
        listOf(),
        emptyMap()
      )

    val opdsEntryBuilder =
      OPDSAcquisitionFeedEntry.newBuilder(
        "a",
        "Title",
        DateTime.now(),
        OPDSAvailabilityLoaned.get(
          Option.none(),
          Option.none(),
          Option.some(this.webServer.url("revoke").toUri())
        )
      )
    opdsEntryBuilder.addAcquisition(acquisition)

    val opdsEntry =
      opdsEntryBuilder.build()

    val book =
      Book(
        id = bookId,
        account = this.accountID,
        cover = null,
        thumbnail = null,
        entry = opdsEntry,
        formats = listOf()
      )
    Mockito.`when`(profile.id)
      .thenReturn(this.profileID)
    Mockito.`when`(profilesDatabase.profiles())
      .thenReturn(ConcurrentSkipListMap(mapOf(this.profileID to profile)))
    Mockito.`when`(profile.account(this.accountID))
      .thenReturn(account)

    val bookDatabaseEntry =
      MockBookDatabaseEntry(book)
    Mockito.`when`(bookDatabase.entry(bookId))
      .thenReturn(bookDatabaseEntry)

    account.bookDatabaseProperty = bookDatabase

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(
          """
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
  <id>436974f2-03ac-4023-8618-84291c6795b7</id>
  <title>436974f2-03ac-4023-8618-84291c6795b7</title>
  <updated>2020-01-01T00:00:00Z</updated>

  <entry>
    <id>15626ed2-ecd7-43ec-8de2-0eb9be2f8c6a</id>
    <title>Open-Access</title>
    <summary type="html"/>
    <updated>2020-01-01T00:00:00Z</updated>
    <published>2020-01-01T00:00:00Z</published>
    <link
        href="https://example.com/borrow"
        type="application/atom+xml;relation=entry;profile=opds-catalog"
        rel="http://opds-spec.org/acquisition">
      <opds:indirectAcquisition type="application/vnd.adobe.adept+xml">
        <opds:indirectAcquisition type="application/epub+zip"/>
      </opds:indirectAcquisition>
      <opds:availability
          status="available"
          since="2000-01-01T00:00:00Z"/>
      <opds:holds total="0"/>
      <opds:copies available="1" total="1"/>
    </link>
  </entry>
</feed>
          """.trimIndent()
        )
    )

    val task =
      BookRevokeTask(
        accountID = account.id,
        profileID = profile.id,
        profiles = profilesDatabase,
        adobeDRM = null,
        bookID = bookId,
        bookRegistry = this.bookRegistry,
        feedLoader = createFeedLoader(),
        revokeStrings = MockRevokeStringResources()
      )

    val result = task.call()
    TaskDumps.dump(this.logger, result)
    result as TaskResult.Success

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  private fun createFeedLoader(): FeedLoaderType {
    val entryParser =
      OPDSAcquisitionFeedEntryParser.newParser()
    val parser =
      OPDSFeedParser.newParser(entryParser)
    val searchParser =
      OPDSSearchParser.newParser()
    val transport =
      FeedHTTPTransport(this.httpClient)

    val feedLoader =
      FeedLoader.create(
        bookFormatSupport = this.bookFormatSupport,
        bundledContent = this.bundledContent,
        contentResolver = this.contentResolver,
        exec = executorFeeds,
        parser = parser,
        searchParser = searchParser,
        transport = transport
      )

    feedLoader.showOnlySupportedBooks = false
    return feedLoader
  }
}
