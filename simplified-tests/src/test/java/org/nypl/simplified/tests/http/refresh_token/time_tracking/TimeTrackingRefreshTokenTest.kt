package org.nypl.simplified.tests.http.refresh_token.time_tracking

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.time.tracking.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingHTTPCalls
import org.nypl.simplified.books.time.tracking.TimeTrackingRequest
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponse
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseSummary
import org.nypl.simplified.tests.mocking.MockAccount
import java.net.URI
import java.util.concurrent.TimeUnit

class TimeTrackingRefreshTokenTest {
  private lateinit var account: MockAccount
  private lateinit var accountID: AccountID
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var webServer: MockWebServer

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
      annotationsURI = URI("https://www.example.com"),
      deviceRegistrationURI = URI("https://www.example.com")
    )

    this.account.setLoginState(AccountLoginState.AccountLoggedIn(credentials))
    this.bookRegistry =
      BookRegistry.create()

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
    this.webServer.close()
  }

  @Test
  fun testSendEntriesUpdateToken() {
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

    val httpCalls = TimeTrackingHTTPCalls(
      http = httpClient
    )

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
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(ObjectMapper().writeValueAsString(responseBody))
    )

    val failedEntries = httpCalls.registerTimeTrackingInfo(
      request = timeTrackingInfo,
      account = account
    )

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }
}
