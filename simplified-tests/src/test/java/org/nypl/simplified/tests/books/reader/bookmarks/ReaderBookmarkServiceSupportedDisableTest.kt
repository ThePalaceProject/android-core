package org.nypl.simplified.tests.books.reader.bookmarks

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.reader.bookmarks.ReaderBookmarkService
import org.nypl.simplified.books.reader.bookmarks.internal.RBHTTPCalls
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceProviderType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableResult.SYNC_DISABLED
import org.nypl.simplified.tests.EventLogging
import org.nypl.simplified.tests.mocking.MockProfilesController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

class ReaderBookmarkServiceSupportedDisableTest {

  val fakeAccountID =
    AccountID(UUID.fromString("46d17029-14ba-4e34-bcaa-def02713575a"))

  private val logger: Logger =
    LoggerFactory.getLogger(ReaderBookmarkServiceSupportedDisableTest::class.java)

  private fun bookmarkService(
    threads: (Runnable) -> Thread,
    events: Subject<ReaderBookmarkEvent>,
    httpCalls: ReaderBookmarkHTTPCallsType,
    profilesController: ProfilesControllerType
  ): ReaderBookmarkServiceType {
    return ReaderBookmarkService.createService(
      ReaderBookmarkServiceProviderType.Requirements(
        threads = threads,
        events = events,
        httpCalls = httpCalls,
        profilesController = profilesController
      )
    )
  }

  private val objectMapper = ObjectMapper()
  private var readerBookmarkService: ReaderBookmarkServiceType? = null
  private lateinit var server: MockWebServer
  private lateinit var http: LSHTTPClientType
  private lateinit var annotationsURI: URI
  private lateinit var patronURI: URI
  private lateinit var serverDispatcher: EndpointDispatcher

  private val annotationsEmpty = """
{
   "id" : "http://www.example.com/annotations/",
   "type" : [
      "BasicContainer",
      "AnnotationCollection"
   ],
   "@context" : [
      "http://www.w3.org/ns/anno.jsonld",
      "http://www.w3.org/ns/ldp.jsonld"
   ],
   "total" : 0,
   "first" : {
      "items" : [],
      "type" : "AnnotationPage",
      "id" : "http://www.example.com/annotations/"
   }
}
"""

  private val patronSettingsWithAnnotationsEnabled = """
{
  "settings": {
    "simplified:synchronize_annotations": true
  }
}
"""

  private val patronSettingsWithAnnotationsDisabled = """
{
  "settings": {
    "simplified:synchronize_annotations": false
  }
}
"""

  private fun addResponse(
    uri: String,
    response: String
  ) {
    this.serverDispatcher.addResponse(
      endpoint = uri,
      response = MockResponse()
        .setResponseCode(200)
        .setBody(response)
    )
  }

  @BeforeEach
  fun setup() {

    /*
     * MockWebServer doesn't handle IPv6 for some reason, but the tests executing on a local
     * machine will try to access the server over IPv6 first on some systems.
     */

    System.setProperty("java.net.preferIPv4Stack", "true")

    this.http =
      LSHTTPClients()
        .create(
          context = Mockito.mock(Context::class.java),
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-test",
            applicationVersion = "0.0.1",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    this.server = MockWebServer()
    this.server.start(
      InetAddress.getByName("127.0.0.1"), 10000
    )
    this.serverDispatcher = EndpointDispatcher()
    this.server.dispatcher = this.serverDispatcher
    this.annotationsURI =
      URI.create("http://localhost:10000/annotations")
    this.patronURI =
      URI.create("http://localhost:10000/patron")
  }

  @AfterEach
  fun tearDown() {
    this.readerBookmarkService?.close()
    this.server.shutdown()
    this.server.close()
  }

  private fun takeAllRequests(): List<RecordedRequest> {
    val requests = mutableListOf<RecordedRequest>()
    for (i in 0 until this.server.requestCount) {
      val request = this.server.takeRequest()
      this.logger.debug("requests [$i]: {}", request.requestUrl?.toUri())
      requests.add(request)
    }
    return requests.toList()
  }

  /**
   * Trying to disable syncing on an account that supports it, makes the appropriate HTTP calls, and
   * returns an appropriate status code.
   */

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testEnableBookmarkSyncingSupportedDisable() {
    val httpCalls =
      RBHTTPCalls(this.objectMapper, this.http)
    val bookmarkEvents =
      EventLogging.create<ReaderBookmarkEvent>(this.logger, 3)
    val profiles =
      MockProfilesController(1, 1)

    val account = profiles.profileList[0].accountList[0]
    account.setAccountProvider(
      (account.provider as AccountProvider).copy(patronSettingsURI = this.patronURI)
    )
    account.setPreferences(account.preferences.copy(bookmarkSyncingPermitted = true))
    account.setLoginState(
      AccountLoggedIn(
        AccountAuthenticationCredentials.Basic(
          userName = AccountUsername("durandal"),
          password = AccountPassword("tycho"),
          adobeCredentials = null,
          authenticationDescription = null,
          annotationsURI = this.annotationsURI
        )
      )
    )

    this.addResponse(
      this.patronURI.path,
      this.patronSettingsWithAnnotationsEnabled
    )

    /*
     * The service then sends a request to turn syncing off, then checks again to see if the patron
     * has syncing disabled.
     */

    this.addResponse(
      this.patronURI.path,
      this.patronSettingsWithAnnotationsDisabled
    )
    this.addResponse(
      this.patronURI.path,
      this.patronSettingsWithAnnotationsDisabled
    )
    this.addResponse(
      this.patronURI.path,
      this.patronSettingsWithAnnotationsDisabled
    )

    this.readerBookmarkService =
      this.bookmarkService(
        threads = ::Thread,
        events = bookmarkEvents.events,
        httpCalls = httpCalls,
        profilesController = profiles
      )

    val service =
      this.readerBookmarkService!!
    val result =
      service.bookmarkSyncEnable(
        accountID = profiles.profileList[0].accountList[0].id,
        enabled = false
      ).get()

    this.waitForServiceQuiescence(service, profiles)

    val allRequests = this.takeAllRequests()
    Assertions.assertTrue(
      allRequests.filter { request ->
        this.matchesEndpoint(request, "/patron")
      }.size > 2,
      "At least two requests made to ${this.patronURI}"
    )
    Assertions.assertEquals(SYNC_DISABLED, result)
    Assertions.assertEquals(false, account.preferences.bookmarkSyncingPermitted)
  }

  /*
 * Wait for the bookmark service to finish all of the requests.
 */

  private fun waitForServiceQuiescence(
    service: ReaderBookmarkServiceType,
    profiles: MockProfilesController
  ) {

    Thread.sleep(1_000L)

    try {
      service.bookmarkLoad(
        accountID = profiles.profileList[0].accountList[0].id,
        book = BookID.create("x")
      ).get(3L, TimeUnit.SECONDS)
    } catch (e: Exception) {
      // Not a problem
    }
  }

  private fun matchesEndpoint(
    request: RecordedRequest,
    endpoint: String
  ): Boolean {
    val requestUri = request.requestUrl?.toUri()
    return requestUri?.path == endpoint
  }
}
