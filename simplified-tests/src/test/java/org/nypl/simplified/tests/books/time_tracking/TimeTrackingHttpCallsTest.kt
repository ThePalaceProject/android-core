package org.nypl.simplified.tests.books.time_tracking

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mock
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.time.tracking.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingHTTPCalls
import org.nypl.simplified.books.time.tracking.TimeTrackingRequest
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponse
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseSummary
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class TimeTrackingHttpCallsTest {
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var webServer: MockWebServer

  @Mock
  private val account: AccountType = Mockito.mock(AccountType::class.java)

  @Mock
  private val loginState: AccountLoginState = Mockito.mock(AccountLoginState::class.java)

  @BeforeEach
  fun testSetup() {
    this.webServer = MockWebServer()
    this.webServer.start(InetAddress.getByName("127.0.0.1"), 20000)

    val credentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("abcd"),
        password = AccountPassword("1234"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com")
      )

    Mockito.`when`(account.loginState).thenReturn(loginState)
    Mockito.`when`(loginState.credentials).thenReturn(credentials)

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
  }

  @Test
  fun testAllEntriesWithSuccess() {
    val timeTrackingInfo = TimeTrackingRequest(
      bookId = "book-id",
      libraryId = URI.create("urn:uuid:f8f6b138-02ba-4624-802b-0556278228d5"),
      timeTrackingUri = this.webServer.url("/timeTracking").toUri(),
      timeEntries = listOf(
        TimeTrackingEntry(
          id = "id",
          duringMinute = "2024-10-16T00:00:00",
          secondsPlayed = 60
        )
      )
    )

    val httpCalls =
      TimeTrackingHTTPCalls(http = httpClient)

    val responseBody = TimeTrackingServerResponse(
      responses = listOf(
        TimeTrackingServerResponseEntry(
          id = "id",
          message = "success",
          status = 201
        )
      ),
      summary = TimeTrackingServerResponseSummary(
        successes = 1,
        failures = 0,
        total = 1
      )
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(ObjectMapper().writeValueAsString(responseBody))
    )

    val response = httpCalls.registerTimeTrackingInfo(
      request = timeTrackingInfo,
      account = account
    )

    assertEquals(1, response.responses.size)
    assertEquals("success", response.responses[0].message)
  }

  @Test
  fun testSomeEntriesWithSuccess() {
    val timeTrackingInfo =
      TimeTrackingRequest(
        bookId = "book-id",
        libraryId = URI.create("urn:uuid:f8f6b138-02ba-4624-802b-0556278228d5"),
        timeTrackingUri = this.webServer.url("/timeTracking").toUri(),
        timeEntries = listOf(
          TimeTrackingEntry(id = "id", duringMinute = "", 10),
          TimeTrackingEntry(id = "id2", duringMinute = "", 10),
          TimeTrackingEntry(id = "id3", duringMinute = "", 10),
          TimeTrackingEntry(id = "id4", duringMinute = "", 10)
        )
      )

    val httpCalls =
      TimeTrackingHTTPCalls(http = httpClient)

    val responseBody = TimeTrackingServerResponse(
      responses = listOf(
        TimeTrackingServerResponseEntry(
          id = "id",
          message = "success",
          status = 201
        ),
        TimeTrackingServerResponseEntry(
          id = "id2",
          message = "gone",
          status = 410
        ),
        TimeTrackingServerResponseEntry(
          id = "id3",
          message = "error",
          status = 400
        ),
        TimeTrackingServerResponseEntry(
          id = "id4",
          message = "another error",
          status = 400
        )
      ),
      summary = TimeTrackingServerResponseSummary(
        successes = 1,
        failures = 3,
        total = 4
      )
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(ObjectMapper().writeValueAsString(responseBody))
    )

    val response = httpCalls.registerTimeTrackingInfo(
      request = timeTrackingInfo,
      account = account
    )

    assertEquals(3, response.responses.filter { t -> !t.isStatusSuccess() }.size)
  }

  @Test
  fun testNoEntriesWithSuccess() {
    val timeTrackingInfo =
      TimeTrackingRequest(
        bookId = "book-id",
        libraryId = URI.create("urn:uuid:f8f6b138-02ba-4624-802b-0556278228d5"),
        timeTrackingUri = this.webServer.url("/timeTracking").toUri(),
        timeEntries = listOf(
          TimeTrackingEntry(id = "id", duringMinute = "", 10),
          TimeTrackingEntry(id = "id2", duringMinute = "", 10),
          TimeTrackingEntry(id = "id3", duringMinute = "", 10)
        )
      )

    val httpCalls = TimeTrackingHTTPCalls(
      http = httpClient
    )

    val responseBody = TimeTrackingServerResponse(
      responses = listOf(
        TimeTrackingServerResponseEntry(
          id = "id",
          message = "error",
          status = 400
        ),
        TimeTrackingServerResponseEntry(
          id = "id2",
          message = "another error",
          status = 400
        ),
        TimeTrackingServerResponseEntry(
          id = "id3",
          message = "and another error",
          status = 400
        )
      ),
      summary = TimeTrackingServerResponseSummary(
        successes = 0,
        failures = 3,
        total = 3
      )
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(ObjectMapper().writeValueAsString(responseBody))
    )

    val response = httpCalls.registerTimeTrackingInfo(
      request = timeTrackingInfo,
      account = account
    )

    assertEquals(3, response.responses.filter { t -> !t.isStatusSuccess() }.size)
  }

  @Test
  fun testAllEntriesWith404() {
    val timeTrackingInfo = TimeTrackingRequest(
      bookId = "book-id",
      libraryId = URI.create("urn:uuid:f8f6b138-02ba-4624-802b-0556278228d5"),
      timeTrackingUri = this.webServer.url("/timeTracking").toUri(),
      timeEntries = listOf(
        TimeTrackingEntry(
          id = "id0",
          duringMinute = "2024-10-16T00:00:00",
          secondsPlayed = 60
        ),
        TimeTrackingEntry(
          id = "id1",
          duringMinute = "2024-10-16T00:00:00",
          secondsPlayed = 60
        ),
        TimeTrackingEntry(
          id = "id2",
          duringMinute = "2024-10-16T00:00:00",
          secondsPlayed = 60
        )
      )
    )

    val httpCalls =
      TimeTrackingHTTPCalls(http = httpClient)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(404)
    )

    val response = httpCalls.registerTimeTrackingInfo(
      request = timeTrackingInfo,
      account = account
    )

    assertEquals(3, response.responses.size)
    assertEquals("Server returned a 404 value. This is a synthesized message.", response.responses[0].message)
    assertEquals("Server returned a 404 value. This is a synthesized message.", response.responses[1].message)
    assertEquals("Server returned a 404 value. This is a synthesized message.", response.responses[2].message)
  }

  @Test
  fun testAllSubEntriesWith404() {
    val timeTrackingInfo =
      TimeTrackingRequest(
        bookId = "book-id",
        libraryId = URI.create("urn:uuid:f8f6b138-02ba-4624-802b-0556278228d5"),
        timeTrackingUri = this.webServer.url("/timeTracking").toUri(),
        timeEntries = listOf(
          TimeTrackingEntry(id = "id", duringMinute = "", 10),
          TimeTrackingEntry(id = "id2", duringMinute = "", 10),
          TimeTrackingEntry(id = "id3", duringMinute = "", 10)
        )
      )

    val httpCalls = TimeTrackingHTTPCalls(
      http = httpClient
    )

    val responseBody = TimeTrackingServerResponse(
      responses = listOf(
        TimeTrackingServerResponseEntry(
          id = "id",
          message = "error",
          status = 404
        ),
        TimeTrackingServerResponseEntry(
          id = "id2",
          message = "another error",
          status = 404
        ),
        TimeTrackingServerResponseEntry(
          id = "id3",
          message = "and another error",
          status = 404
        )
      ),
      summary = TimeTrackingServerResponseSummary(
        successes = 0,
        failures = 3,
        total = 3
      )
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(ObjectMapper().writeValueAsString(responseBody))
    )

    val response = httpCalls.registerTimeTrackingInfo(
      request = timeTrackingInfo,
      account = account
    )

    assertEquals(
      3,
      response.responses.filter(TimeTrackingServerResponseEntry::isStatusFailedPermanently).size
    )
  }
}
