package org.nypl.simplified.tests.books.time_tracking

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.books.time.tracking.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingHTTPCalls
import org.nypl.simplified.books.time.tracking.TimeTrackingInfo
import org.nypl.simplified.books.time.tracking.TimeTrackingResponse
import org.nypl.simplified.books.time.tracking.TimeTrackingResponseEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingResponseSummary
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class TimeTrackingHttpCallsTest {
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var webServer: MockWebServer

  @BeforeEach
  fun testSetup() {
    this.webServer = MockWebServer()
    this.webServer.start(InetAddress.getByName("127.0.0.1"), 20000)

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
    val timeTrackingInfo = Mockito.mock(TimeTrackingInfo::class.java)
    Mockito.`when`(timeTrackingInfo.timeTrackingUri)
      .thenReturn(this.webServer.url("/timeTracking").toUri())

    val httpCalls = TimeTrackingHTTPCalls(
      objectMapper = ObjectMapper(),
      http = httpClient
    )

    val credentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("abcd"),
        password = AccountPassword("1234"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com")
      )

    val responseBody = TimeTrackingResponse(
      responses = listOf(
        TimeTrackingResponseEntry(
          id = "id",
          message = "success",
          status = 201
        )
      ),
      summary = TimeTrackingResponseSummary(
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

    val failedEntries = httpCalls.registerTimeTrackingInfo(
      timeTrackingInfo = timeTrackingInfo,
      credentials = credentials
    )

    assertTrue(failedEntries.isEmpty())
  }

  @Test
  fun testSomeEntriesWithSuccess() {
    val timeTrackingInfo = Mockito.mock(TimeTrackingInfo::class.java)
    Mockito.`when`(timeTrackingInfo.timeEntries)
      .thenReturn(
        listOf(
          TimeTrackingEntry(id = "id", duringMinute = "", 10),
          TimeTrackingEntry(id = "id2", duringMinute = "", 10),
          TimeTrackingEntry(id = "id3", duringMinute = "", 10),
          TimeTrackingEntry(id = "id4", duringMinute = "", 10)
        )
      )
    Mockito.`when`(timeTrackingInfo.timeTrackingUri)
      .thenReturn(this.webServer.url("/timeTracking").toUri())

    val httpCalls = TimeTrackingHTTPCalls(
      objectMapper = ObjectMapper(),
      http = httpClient
    )

    val credentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("abcd"),
        password = AccountPassword("1234"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com")
      )

    val responseBody = TimeTrackingResponse(
      responses = listOf(
        TimeTrackingResponseEntry(
          id = "id",
          message = "success",
          status = 201
        ),
        TimeTrackingResponseEntry(
          id = "id2",
          message = "gone",
          status = 410
        ),
        TimeTrackingResponseEntry(
          id = "id3",
          message = "error",
          status = 400
        ),
        TimeTrackingResponseEntry(
          id = "id4",
          message = "another error",
          status = 400
        )
      ),
      summary = TimeTrackingResponseSummary(
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

    val failedEntries = httpCalls.registerTimeTrackingInfo(
      timeTrackingInfo = timeTrackingInfo,
      credentials = credentials
    )

    assertTrue(failedEntries.size == 2)
  }

  @Test
  fun testNoEntriesWithSuccess() {
    val timeTrackingInfo = Mockito.mock(TimeTrackingInfo::class.java)
    Mockito.`when`(timeTrackingInfo.timeEntries)
      .thenReturn(
        listOf(
          TimeTrackingEntry(id = "id", duringMinute = "", 10),
          TimeTrackingEntry(id = "id2", duringMinute = "", 10),
          TimeTrackingEntry(id = "id3", duringMinute = "", 10)
        )
      )
    Mockito.`when`(timeTrackingInfo.timeTrackingUri)
      .thenReturn(this.webServer.url("/timeTracking").toUri())

    val httpCalls = TimeTrackingHTTPCalls(
      objectMapper = ObjectMapper(),
      http = httpClient
    )

    val credentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("abcd"),
        password = AccountPassword("1234"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com")
      )

    val responseBody = TimeTrackingResponse(
      responses = listOf(
        TimeTrackingResponseEntry(
          id = "id",
          message = "error",
          status = 400
        ),
        TimeTrackingResponseEntry(
          id = "id2",
          message = "another error",
          status = 400
        ),
        TimeTrackingResponseEntry(
          id = "id3",
          message = "and another error",
          status = 400
        )
      ),
      summary = TimeTrackingResponseSummary(
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

    val failedEntries = httpCalls.registerTimeTrackingInfo(
      timeTrackingInfo = timeTrackingInfo,
      credentials = credentials
    )

    assertTrue(failedEntries.size == 3)
  }
}
