package org.nypl.simplified.tests.books.accounts

import android.content.Context
import android.content.res.Resources
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.joda.time.DateTimeUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderSourceNYPLRegistry
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType.SourceResult.SourceSucceeded
import org.nypl.simplified.opds.auth_document.AuthenticationDocumentParsers
import org.nypl.simplified.opds2.irradia.OPDS2ParsersIrradia
import org.nypl.simplified.tests.mocking.MockAccountProviderResolutionStrings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class AccountProviderNYPLRegistryTest {

  private lateinit var server: MockWebServer
  private lateinit var http: LSHTTPClientType
  private lateinit var resources: Resources
  private lateinit var context: Context

  private val logger: Logger =
    LoggerFactory.getLogger(AccountProviderNYPLRegistryTest::class.java)

  private val opdsParsers = OPDS2ParsersIrradia

  @Throws(Exception::class)
  private fun readAllFromResource(name: String): InputStream {
    return AccountProviderNYPLRegistryTest::class.java
      .getResource("/org/nypl/simplified/tests/books/accounts/descriptions/$name")!!
      .openStream()
  }

  @BeforeEach
  fun testSetup() {
    this.resources = Mockito.mock(Resources::class.java)
    this.context = Mockito.mock(Context::class.java)

    Mockito.`when`(this.context.resources)
      .thenReturn(this.resources)
    Mockito.`when`(this.resources.getString(Mockito.anyInt()))
      .thenReturn("A STRING RESOURCE")

    this.http =
      LSHTTPClients()
        .create(
          context = Mockito.mock(Context::class.java),
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-tests",
            applicationVersion = "1.0",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    this.server = MockWebServer()
    this.server.start()
  }

  @AfterEach
  fun testTearDown() {
    this.server.close()
  }

  /**
   * The correct providers are returned from the server.
   */

  @Test
  fun testProductionProvidersFromServerOK() {
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(this.readAllFromResource("libraryregistry.json")))
    )

    val provider =
      AccountProviderSourceNYPLRegistry(
        authDocumentParsers = AuthenticationDocumentParsers(),
        http = this.http,
        parsers = AccountProviderDescriptionCollectionParsers(this.opdsParsers),
        stringResources = MockAccountProviderResolutionStrings(),
        uriProduction = this.server.url("production").toUri(),
        uriQA = this.server.url("qa").toUri(),
      )

    val result = provider.load(this.context, false)
    this.logger.debug("status: {}", result)
    val success = result as SourceSucceeded

    Assertions.assertEquals(43, success.results.size)
  }

  /**
   * The correct providers are returned from the server.
   */

  @Test
  fun testAllProvidersFromServerOK() {
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(this.readAllFromResource("libraryregistry-qa.json")))
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(this.readAllFromResource("libraryregistry.json")))
    )

    val provider =
      AccountProviderSourceNYPLRegistry(
        http = this.http,
        authDocumentParsers = AuthenticationDocumentParsers(),
        parsers = AccountProviderDescriptionCollectionParsers(this.opdsParsers),
        uriProduction = this.server.url("production").toUri(),
        uriQA = this.server.url("qa").toUri(),
        stringResources = MockAccountProviderResolutionStrings(),
      )

    val result = provider.load(this.context, true)
    this.logger.debug("status: {}", result)
    val success = result as SourceSucceeded

    Assertions.assertEquals(182, success.results.size)
  }

  /**
   * If the server returns garbage, the source fails.
   */

  @Test
  fun testProvidersBadEverywhere() {
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("Nonsense! Tripe! Ungood data!")
    )

    val provider =
      AccountProviderSourceNYPLRegistry(
        http = this.http,
        authDocumentParsers = AuthenticationDocumentParsers(),
        parsers = AccountProviderDescriptionCollectionParsers(this.opdsParsers),
        uriProduction = this.server.url("production").toUri(),
        uriQA = this.server.url("qa").toUri(),
        stringResources = MockAccountProviderResolutionStrings(),
      )

    val result = provider.load(this.context, true)
    this.logger.debug("status: {}", result)
    val failed = result as AccountProviderSourceType.SourceResult.SourceFailed

    Assertions.assertEquals(0, failed.results.size)
  }

  /**
   * The providers are queried from the server.
   */

  @Test
  fun testProvidersRefreshOK() {
    val provider =
      AccountProviderSourceNYPLRegistry(
        http = this.http,
        authDocumentParsers = AuthenticationDocumentParsers(),
        parsers = AccountProviderDescriptionCollectionParsers(this.opdsParsers),
        uriProduction = this.server.url("production").toUri(),
        uriQA = this.server.url("qa").toUri(),
        stringResources = MockAccountProviderResolutionStrings(),
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(this.readAllFromResource("libraryregistry.json")))
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(this.readAllFromResource("libraryregistry-qa.json")))
    )

    this.run {
      val result = provider.load(this.context, true)
      this.logger.debug("status: {}", result)
      val success = result as SourceSucceeded

      Assertions.assertEquals(43, success.results.size)
    }

    this.run {
      val result = provider.load(this.context, true)
      this.logger.debug("status: {}", result)
      val success = result as SourceSucceeded

      Assertions.assertEquals(182, success.results.size)
    }

    // Reset
    DateTimeUtils.setCurrentMillisSystem()
  }
}
