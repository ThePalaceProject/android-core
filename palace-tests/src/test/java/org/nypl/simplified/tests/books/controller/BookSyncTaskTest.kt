package org.nypl.simplified.tests.books.controller

import android.content.Context
import com.google.common.util.concurrent.ListeningExecutorService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPProblemReport
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.controller.BookSyncTask
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.patron.PatronUserProfileParsers
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.books.controller.FakeAccounts.fakeAccountProvider
import org.nypl.simplified.tests.mocking.FakeBooksController
import org.nypl.simplified.tests.mocking.FakeFeedLoader
import org.nypl.simplified.tests.mocking.FakeProfilesDatabase
import org.nypl.simplified.tests.mocking.MockAccountProviderRegistry
import org.nypl.simplified.tests.mocking.MockProfile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Contract for the `BookSyncTask` class.
 */

class BookSyncTaskTest {

  val profileID =
    ProfileID(UUID.fromString("06fa7899-658a-4480-a796-ebf2ff00d5ec"))

  private val logger: Logger =
    LoggerFactory.getLogger(BookSyncTaskTest::class.java)

  private lateinit var bookRegistry: BookRegistryType
  private lateinit var http: LSHTTPClientType
  private lateinit var server: MockWebServer

  @BeforeEach
  @Throws(Exception::class)
  fun setUp() {
    this.http =
      LSHTTPClients()
        .create(
          context = Mockito.mock(Context::class.java),
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-tests",
            applicationVersion = "1.0.0",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    this.bookRegistry = BookRegistry.create()

    this.server = MockWebServer()
    this.server.start()
  }

  @AfterEach
  @Throws(Exception::class)
  fun tearDown() {
    this.server.close()
  }

  @Test
  fun testSync401_0() {
    val accountProvider =
      fakeAccountProvider()

    val fakeProfilesDatabase =
      FakeProfilesDatabase()
    val fakeBooksController =
      FakeBooksController()
    val fakeProfile =
      MockProfile(this.profileID, 1)

    fakeProfilesDatabase.profileMap[this.profileID] = fakeProfile

    val fakeAccount =
      fakeProfile.accountList[0]
    val accountID =
      fakeAccount.id

    val fakeAccountProvider =
      (fakeAccount.provider as AccountProvider)

    fakeAccount.setAccountProvider(
      fakeAccountProvider.copy(
        loansURI = this.server.url("/loans").toUri(),
        patronSettingsURI = this.server.url("/patron").toUri(),
      )
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(401)
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(401)
    )

    fakeAccount.setLoginState(AccountLoginState.AccountLoggedIn(
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("someone"),
        password = AccountPassword("password"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = null,
        deviceRegistrationURI = null
      )
    ))

    val accountRegistry =
      MockAccountProviderRegistry.singleton(accountProvider)
    val opdsParser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
    val feedLoader =
      FakeFeedLoader()

    Assertions.assertFalse(fakeAccount.credentialsExpired, "Credentials must have expired.")

    val task =
      BookSyncTask(
        accountID = accountID,
        profileID = profileID,
        profiles = fakeProfilesDatabase,
        bookRegistry = this.bookRegistry,
        feedLoader = feedLoader,
        patronParsers = PatronUserProfileParsers(),
        http = this.http,
        accountRegistry = accountRegistry,
        booksController = fakeBooksController,
        feedParser = opdsParser
      )

    val result = task.call()
    TaskDumps.dump(this.logger, result)
    result as TaskResult.Failure

    Assertions.assertFalse(fakeAccount.credentialsExpired, "Credentials must not have expired.")
  }

  @Test
  fun testSync401_1() {
    val accountProvider =
      fakeAccountProvider()

    val fakeProfilesDatabase =
      FakeProfilesDatabase()
    val fakeBooksController =
      FakeBooksController()
    val fakeProfile =
      MockProfile(this.profileID, 1)

    fakeProfilesDatabase.profileMap[this.profileID] = fakeProfile

    val fakeAccount =
      fakeProfile.accountList[0]
    val accountID =
      fakeAccount.id

    val fakeAccountProvider =
      (fakeAccount.provider as AccountProvider)

    fakeAccount.setAccountProvider(
      fakeAccountProvider.copy(
        loansURI = this.server.url("/loans").toUri(),
        patronSettingsURI = this.server.url("/patron").toUri(),
      )
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resource("/org/nypl/simplified/tests/patron/example.json"))
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(401)
    )

    fakeAccount.setLoginState(AccountLoginState.AccountLoggedIn(
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("someone"),
        password = AccountPassword("password"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = null,
        deviceRegistrationURI = null
      )
    ))

    val accountRegistry =
      MockAccountProviderRegistry.singleton(accountProvider)
    val opdsParser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())

    val feedLoader =
      FakeFeedLoader()

    feedLoader.addResponse(
      FeedLoaderFailedAuthentication(
        problemReport = null,
        exception = Exception(),
        message = "Access Denied",
        attributesInitial = mapOf()
      )
    )

    Assertions.assertFalse(fakeAccount.credentialsExpired, "Credentials must not have expired.")

    val task =
      BookSyncTask(
        accountID = accountID,
        profileID = profileID,
        profiles = fakeProfilesDatabase,
        bookRegistry = this.bookRegistry,
        feedLoader = feedLoader,
        patronParsers = PatronUserProfileParsers(),
        http = this.http,
        accountRegistry = accountRegistry,
        booksController = fakeBooksController,
        feedParser = opdsParser
      )

    val result = task.call()
    TaskDumps.dump(this.logger, result)
    result as TaskResult.Failure

    Assertions.assertFalse(fakeAccount.credentialsExpired, "Credentials must not have expired.")
  }

  @Test
  fun testSync401_2() {
    val accountProvider =
      fakeAccountProvider()

    val fakeProfilesDatabase =
      FakeProfilesDatabase()
    val fakeBooksController =
      FakeBooksController()
    val fakeProfile =
      MockProfile(this.profileID, 1)

    fakeProfilesDatabase.profileMap[this.profileID] = fakeProfile

    val fakeAccount =
      fakeProfile.accountList[0]
    val accountID =
      fakeAccount.id

    val fakeAccountProvider =
      (fakeAccount.provider as AccountProvider)

    fakeAccount.setAccountProvider(
      fakeAccountProvider.copy(
        loansURI = this.server.url("/loans").toUri(),
        patronSettingsURI = this.server.url("/patron").toUri(),
      )
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resource("/org/nypl/simplified/tests/patron/example.json"))
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/api-problem+json")
        .setBody("""
{
  "status": 401,
  "type": "http://palaceproject.io/terms/problem/auth/recoverable/token/expired",
  "title": "Expired!",
  "detail": "Expiration in detail"
}
        """.trimIndent())
    )

    fakeAccount.setLoginState(AccountLoginState.AccountLoggedIn(
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("someone"),
        password = AccountPassword("password"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = null,
        deviceRegistrationURI = null
      )
    ))

    val accountRegistry =
      MockAccountProviderRegistry.singleton(accountProvider)
    val opdsParser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())

    val feedLoader =
      FakeFeedLoader()

    feedLoader.addResponse(
      FeedLoaderFailedAuthentication(
        problemReport = null,
        exception = Exception(),
        message = "Access Denied",
        attributesInitial = mapOf()
      )
    )

    Assertions.assertFalse(fakeAccount.credentialsExpired, "Credentials must have expired.")

    val task =
      BookSyncTask(
        accountID = accountID,
        profileID = profileID,
        profiles = fakeProfilesDatabase,
        bookRegistry = this.bookRegistry,
        feedLoader = feedLoader,
        patronParsers = PatronUserProfileParsers(),
        http = this.http,
        accountRegistry = accountRegistry,
        booksController = fakeBooksController,
        feedParser = opdsParser
      )

    val result = task.call()
    TaskDumps.dump(this.logger, result)
    result as TaskResult.Failure

    Assertions.assertTrue(fakeAccount.credentialsExpired, "Credentials must have expired.")
  }

  @Test
  fun testSync401_3() {
    val accountProvider =
      fakeAccountProvider()

    val fakeProfilesDatabase =
      FakeProfilesDatabase()
    val fakeBooksController =
      FakeBooksController()
    val fakeProfile =
      MockProfile(this.profileID, 1)

    fakeProfilesDatabase.profileMap[this.profileID] = fakeProfile

    val fakeAccount =
      fakeProfile.accountList[0]
    val accountID =
      fakeAccount.id

    val fakeAccountProvider =
      (fakeAccount.provider as AccountProvider)

    fakeAccount.setAccountProvider(
      fakeAccountProvider.copy(
        loansURI = this.server.url("/loans").toUri(),
        patronSettingsURI = this.server.url("/patron").toUri(),
      )
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resource("/org/nypl/simplified/tests/patron/example.json"))
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/api-problem+json")
        .setBody("""
{
  "status": 401,
  "type": "http://palaceproject.io/terms/problem/auth/recoverable/token/expired",
  "title": "Expired!",
  "detail": "Expiration in detail"
}
        """.trimIndent())
    )

    fakeAccount.setLoginState(AccountLoginState.AccountLoggedIn(
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("someone"),
        password = AccountPassword("password"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = null,
        deviceRegistrationURI = null
      )
    ))

    val accountRegistry =
      MockAccountProviderRegistry.singleton(accountProvider)
    val opdsParser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())

    val feedLoader =
      FakeFeedLoader()

    feedLoader.addResponse(
      FeedLoaderFailedAuthentication(
        problemReport = LSHTTPProblemReport(
          status = 401,
          title = "Expired",
          detail = "Expiration in detail",
          type = "http://palaceproject.io/terms/problem/auth/recoverable/token/expired"
        ),
        exception = Exception(),
        message = "Access Denied",
        attributesInitial = mapOf()
      )
    )

    Assertions.assertFalse(fakeAccount.credentialsExpired, "Credentials must have expired.")

    val task =
      BookSyncTask(
        accountID = accountID,
        profileID = profileID,
        profiles = fakeProfilesDatabase,
        bookRegistry = this.bookRegistry,
        feedLoader = feedLoader,
        patronParsers = PatronUserProfileParsers(),
        http = this.http,
        accountRegistry = accountRegistry,
        booksController = fakeBooksController,
        feedParser = opdsParser
      )

    val result = task.call()
    TaskDumps.dump(this.logger, result)
    result as TaskResult.Failure

    Assertions.assertTrue(fakeAccount.credentialsExpired, "Credentials must have expired.")
  }


  private fun resource(file: String): Buffer {
    val buffer = Buffer()
    buffer.readFrom(BookSyncTaskTest::class.java.getResourceAsStream(file)!!)
    return buffer
  }
}
