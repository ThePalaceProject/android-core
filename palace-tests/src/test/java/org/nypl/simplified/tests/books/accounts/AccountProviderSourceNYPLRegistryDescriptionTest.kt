package org.nypl.simplified.tests.books.accounts

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import one.irradia.mime.api.MIMEType
import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.BASIC_TYPE
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderResolution
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocument
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParserType
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.opds.auth_document.api.AuthenticationObject
import org.nypl.simplified.opds.auth_document.api.AuthenticationObjectNYPLFeatures
import org.nypl.simplified.opds.auth_document.api.AuthenticationObjectNYPLInput
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.mocking.MockAccountProviderResolutionStrings
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class AccountProviderSourceNYPLRegistryDescriptionTest {

  private lateinit var server: MockWebServer
  private lateinit var http: LSHTTPClientType
  private lateinit var authDocumentParser: AuthenticationDocumentParserType
  private lateinit var authDocumentParsers: AuthenticationDocumentParsersType
  private lateinit var stringResources: MockAccountProviderResolutionStrings

  private val logger =
    LoggerFactory.getLogger(AccountProviderSourceNYPLRegistryDescriptionTest::class.java)
  private val currentDateTimeZoneSystem = DateTimeZone.getDefault()

  @BeforeEach
  fun testSetup() {
    DateTimeUtils.setCurrentMillisFixed(0L)
    DateTimeZone.setDefault(DateTimeZone.UTC)

    this.stringResources =
      MockAccountProviderResolutionStrings()
    this.authDocumentParsers =
      Mockito.mock(AuthenticationDocumentParsersType::class.java)
    this.authDocumentParser =
      Mockito.mock(AuthenticationDocumentParserType::class.java)
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
  fun tearDown() {
    DateTimeUtils.setCurrentMillisSystem()
    DateTimeZone.setDefault(currentDateTimeZoneSystem)
    this.server.close()
  }

  /**
   * Resolution fails if there is no authentication document URI and no starting URI.
   */

  @Test
  fun testMissingURI() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Failure
    Assertions.assertEquals("resolvingAuthDocumentNoStartURI", result.steps.last().resolution.message)
  }

  private val AUTH_DOCUMENT_TYPE =
    MIMEType("application", "vnd.opds.authentication.v1.0+json", mapOf())

  /**
   * Resolution fails the authentication document can't be retrieved.
   */

  @Test
  fun testAuthDocumentFetchFails() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            URI.create("http://www.example.com/auth"),
            AUTH_DOCUMENT_TYPE
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setStatus("BAD REQUEST")
        .setBody("")
    )

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Failure
    Assertions.assertEquals("resolvingAuthDocumentRetrievalFailed", result.steps.last().resolution.message)
  }

  /**
   * Resolution fails the authentication document can't be retrieved.
   */

  @Test
  fun testAuthDocumentFetchFailsException() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            this.server.url("auth").toUri(),
            AUTH_DOCUMENT_TYPE
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Failure
    Assertions.assertEquals("resolvingUnexpectedException", result.steps.last().resolution.message)
  }

  /**
   * Resolution fails the authentication document can't be parsed.
   */

  @Test
  fun testAuthDocumentUnparseable() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            this.server.url("auth").toUri(),
            AUTH_DOCUMENT_TYPE
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("")
    )

    Mockito.`when`(
      this.authDocumentParsers.createParser(anyNotNull(), anyNotNull(), Mockito.anyBoolean())
    )
      .thenReturn(this.authDocumentParser)

    Mockito.`when`(this.authDocumentParser.parse())
      .thenReturn(ParseResult.Failure(listOf(), listOf()))

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Failure
    Assertions.assertEquals("resolvingAuthDocumentParseFailed", result.steps.last().resolution.message)
  }

  /**
   * Resolution succeeds if nothing fails.
   */

  @Test
  fun testAuthDocumentOK() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            this.server.url("auth").toUri(),
            AUTH_DOCUMENT_TYPE
          ),
          Link.LinkBasic(
            href = URI("https://www.example.com/alternate"),
            relation = "alternate"
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("")
    )

    Mockito.`when`(
      this.authDocumentParsers.createParser(anyNotNull(), anyNotNull(), Mockito.anyBoolean())
    ).thenReturn(this.authDocumentParser)

    val authDocument =
      AuthenticationDocument(
        announcements = emptyList(),
        id = URI("http://www.example.com/auth"),
        title = "Auth",
        mainColor = "blue",
        description = "Some library you've never heard of",
        features = AuthenticationObjectNYPLFeatures(
          enabled = setOf("https://librarysimplified.org/rel/policy/reservations"),
          disabled = setOf()
        ),
        authentication = listOf(
          AuthenticationObject(
            type = URI(BASIC_TYPE),
            description = "Basic Auth",
            labels = mapOf(
              Pair("LOGIN", "LOGIN!"),
              Pair("PASSWORD", "PASSWORD!")
            ),
            inputs = mapOf(
              Pair(
                "LOGIN",
                AuthenticationObjectNYPLInput(
                  fieldName = "LOGIN",
                  keyboardType = "DEFAULT",
                  maximumLength = 20,
                  barcodeFormat = "CODABAR"
                )
              ),
              Pair(
                "PASSWORD",
                AuthenticationObjectNYPLInput(
                  fieldName = "PASSWORD",
                  keyboardType = "DEFAULT",
                  maximumLength = 20,
                  barcodeFormat = "CODABAR"
                )
              )
            )
          )
        ),
        links = listOf(
          Link.LinkBasic(
            href = URI("http://www.example.com/feed.xml"),
            relation = "start"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/card.xml"),
            relation = "register"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/license.xml"),
            relation = "license"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/eula.xml"),
            relation = "terms-of-service"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/settings.xml"),
            relation = "http://librarysimplified.org/terms/rel/user-profile"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/privacy.xml"),
            relation = "privacy-policy"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/shelf.xml"),
            relation = "http://opds-spec.org/shelf"
          ),
          Link.LinkBasic(
            href = URI("mailto:someone@example.com"),
            relation = "help"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/logo.png"),
            relation = "logo"
          )
        )
      )

    Mockito.`when`(this.authDocumentParser.parse())
      .thenReturn(ParseResult.Success(listOf(), authDocument))

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Success

    val provider = AccountProvider(
      announcements = emptyList(),
      authentication = AccountProviderAuthenticationDescription.Basic(
        description = "Basic Auth",
        barcodeFormat = "CODABAR",
        keyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
        passwordMaximumLength = 20,
        passwordKeyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
        labels = mapOf(
          Pair("LOGIN", "LOGIN!"),
          Pair("PASSWORD", "PASSWORD!")
        ),
        logoURI = null
      ),
      authenticationAlternatives = listOf(),
      authenticationDocumentURI = this.server.url("auth").toUri(),
      cardCreatorURI = URI("http://www.example.com/card.xml"),
      catalogURI = URI("http://www.example.com/feed.xml"),
      description = "Some library you've never heard of",
      displayName = "Auth",
      eula = URI("http://www.example.com/eula.xml"),
      id = URI.create("urn:fake:0"),
      license = URI("http://www.example.com/license.xml"),
      loansURI = URI("http://www.example.com/shelf.xml"),
      logo = URI("http://www.example.com/logo.png"),
      mainColor = "blue",
      patronSettingsURI = URI("http://www.example.com/settings.xml"),
      privacyPolicy = URI("http://www.example.com/privacy.xml"),
      resetPasswordURI = null,
      subtitle = "Some library you've never heard of",
      supportEmail = "mailto:someone@example.com",
      supportsReservations = true,
      updated = OffsetDateTime.now(ZoneOffset.UTC).withNano(0),
      alternateURI = URI.create("https://www.example.com/alternate")
    )

    compareProviderValues(provider, result)
  }

  /**
   * Resolution succeeds if nothing fails.
   */

  @Test
  fun testAuthDocumentOK_NoAuth() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            this.server.url("auth").toUri(),
            AUTH_DOCUMENT_TYPE
          ),
          Link.LinkBasic(
            href = URI("https://www.example.com/alternate"),
            relation = "alternate"
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("")
    )

    Mockito.`when`(
      this.authDocumentParsers.createParser(anyNotNull(), anyNotNull(), Mockito.anyBoolean())
    ).thenReturn(this.authDocumentParser)

    val authDocument =
      AuthenticationDocument(
        id = URI("http://www.example.com/auth"),
        announcements = emptyList(),
        title = "Auth",
        mainColor = "blue",
        description = "Some library you've never heard of",
        features = AuthenticationObjectNYPLFeatures(
          enabled = setOf("https://librarysimplified.org/rel/policy/reservations"),
          disabled = setOf()
        ),
        authentication = listOf(),
        links = listOf(
          Link.LinkBasic(
            href = URI("http://www.example.com/feed.xml"),
            relation = "start"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/card.xml"),
            relation = "register"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/license.xml"),
            relation = "license"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/eula.xml"),
            relation = "terms-of-service"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/settings.xml"),
            relation = "http://librarysimplified.org/terms/rel/user-profile"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/privacy.xml"),
            relation = "privacy-policy"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/shelf.xml"),
            relation = "http://opds-spec.org/shelf"
          ),
          Link.LinkBasic(
            href = URI("mailto:someone@example.com"),
            relation = "help"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/logo.png"),
            relation = "logo"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/reset-password"),
            relation = "http://librarysimplified.org/terms/rel/patron-password-reset"
          )
        )
      )

    Mockito.`when`(this.authDocumentParser.parse())
      .thenReturn(ParseResult.Success(listOf(), authDocument))

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Success

    val provider = AccountProvider(
      announcements = emptyList(),
      authentication = AccountProviderAuthenticationDescription.Anonymous,
      authenticationAlternatives = listOf(),
      authenticationDocumentURI = this.server.url("auth").toUri(),
      cardCreatorURI = URI("http://www.example.com/card.xml"),
      catalogURI = URI("http://www.example.com/feed.xml"),
      description = "Some library you've never heard of",
      displayName = "Auth",
      eula = URI("http://www.example.com/eula.xml"),
      id = URI.create("urn:fake:0"),
      license = URI("http://www.example.com/license.xml"),
      loansURI = URI("http://www.example.com/shelf.xml"),
      logo = URI("http://www.example.com/logo.png"),
      mainColor = "blue",
      patronSettingsURI = URI("http://www.example.com/settings.xml"),
      privacyPolicy = URI("http://www.example.com/privacy.xml"),
      resetPasswordURI = URI("http://www.example.com/reset-password"),
      subtitle = "Some library you've never heard of",
      supportEmail = "mailto:someone@example.com",
      supportsReservations = true,
      updated = OffsetDateTime.now(ZoneOffset.UTC).withNano(0),
      alternateURI = URI.create("https://www.example.com/alternate")
    )

    compareProviderValues(provider, result)
  }

  private fun compareProviderValues(
      provider: AccountProvider,
      result: TaskResult.Success<AccountProviderType>
  ) {
    Assertions.assertEquals(provider.announcements, result.result.announcements)
    Assertions.assertEquals(provider.authentication, result.result.authentication)
    Assertions.assertEquals(
      provider.authenticationAlternatives,
      result.result.authenticationAlternatives
    )
    Assertions.assertEquals(
      provider.authenticationDocumentURI,
      result.result.authenticationDocumentURI
    )
    Assertions.assertEquals(provider.cardCreatorURI, result.result.cardCreatorURI)
    Assertions.assertEquals(provider.catalogURI, result.result.catalogURI)
    Assertions.assertEquals(provider.description, result.result.description)
    Assertions.assertEquals(provider.displayName, result.result.displayName)
    Assertions.assertEquals(provider.eula, result.result.eula)
    Assertions.assertEquals(provider.id, result.result.id)
    Assertions.assertEquals(provider.license, result.result.license)
    Assertions.assertEquals(provider.loansURI, result.result.loansURI)
    Assertions.assertEquals(provider.logo, result.result.logo)
    Assertions.assertEquals(provider.mainColor, result.result.mainColor)
    Assertions.assertEquals(provider.patronSettingsURI, result.result.patronSettingsURI)
    Assertions.assertEquals(provider.privacyPolicy, result.result.privacyPolicy)
    Assertions.assertEquals(provider.resetPasswordURI, result.result.resetPasswordURI)
    Assertions.assertEquals(provider.subtitle, result.result.subtitle)
    Assertions.assertEquals(provider.supportEmail, result.result.supportEmail)
    Assertions.assertEquals(provider.supportsReservations, result.result.supportsReservations)
    // Assertions.assertEquals(provider.updated, result.result.updated)
    Assertions.assertEquals(provider.alternateURI, result.result.alternateURI)
  }

  /**
   * Resolution fails if the authentication document contains only authentication types that
   * we can't understand.
   */

  @Test
  fun testAuthDocumentFails_OnlyUnrecognizedAuth() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            this.server.url("auth").toUri(),
            AUTH_DOCUMENT_TYPE
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("")
    )

    Mockito.`when`(
      this.authDocumentParsers.createParser(anyNotNull(), anyNotNull(), Mockito.anyBoolean())
    ).thenReturn(this.authDocumentParser)

    val authDocument =
      AuthenticationDocument(
        id = URI("http://www.example.com/auth"),
        title = "Auth",
        mainColor = "blue",
        description = "Some library you've never heard of",
        features = AuthenticationObjectNYPLFeatures(
          enabled = setOf("https://librarysimplified.org/rel/policy/reservations"),
          disabled = setOf()
        ),
        announcements = emptyList(),
        authentication = listOf(
          AuthenticationObject(
            type = URI("urn:unknown"),
            description = "COPPA Age Gate",
            labels = mapOf(),
            inputs = mapOf(),
            links = listOf()
          )
        ),
        links = listOf(
          Link.LinkBasic(
            href = URI("http://www.example.com/feed.xml"),
            relation = "start"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/card.xml"),
            relation = "register"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/license.xml"),
            relation = "license"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/eula.xml"),
            relation = "terms-of-service"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/settings.xml"),
            relation = "http://librarysimplified.org/terms/rel/user-profile"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/privacy.xml"),
            relation = "privacy-policy"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/shelf.xml"),
            relation = "http://opds-spec.org/shelf"
          ),
          Link.LinkBasic(
            href = URI("mailto:someone@example.com"),
            relation = "help"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/logo.png"),
            relation = "logo"
          )
        )
      )

    Mockito.`when`(this.authDocumentParser.parse())
      .thenReturn(ParseResult.Success(listOf(), authDocument))

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Failure
    Assertions.assertEquals("resolvingAuthDocumentNoUsableAuthenticationTypes", result.steps.last().resolution.message)
  }

  /**
   * Resolution fails if the authentication document doesn't provide a catalog URI.
   */

  @Test
  fun testAuthDocumentFails_NoCatalogURI() {
    val metadata =
      AccountProviderDescription(
        id = URI.create("urn:fake:0"),
        title = "Title",
        description = "Description",
        updated = OffsetDateTime.parse("2019-07-09T08:33:40+00:00"),
        links = listOf(
          Link.LinkBasic(
            this.server.url("auth").toUri(),
            AUTH_DOCUMENT_TYPE
          )
        ),
        images = listOf(),
      )

    val description =
      AccountProviderResolution(
        stringResources = this.stringResources,
        authDocumentParsers = this.authDocumentParsers,
        http = this.http,
        description = metadata
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("")
    )

    Mockito.`when`(
      this.authDocumentParsers.createParser(anyNotNull(), anyNotNull(), Mockito.anyBoolean())
    )
      .thenReturn(this.authDocumentParser)

    val authDocument =
      AuthenticationDocument(
        id = URI("http://www.example.com/auth"),
        title = "Auth",
        mainColor = "blue",
        description = "Some library you've never heard of",
        authentication = listOf(),
        announcements = emptyList(),
        features = AuthenticationObjectNYPLFeatures(
          enabled = setOf("https://librarysimplified.org/rel/policy/reservations"),
          disabled = setOf()
        ),
        links = listOf(
          Link.LinkBasic(
            href = URI("http://www.example.com/card.xml"),
            relation = "register"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/license.xml"),
            relation = "license"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/eula.xml"),
            relation = "terms-of-service"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/settings.xml"),
            relation = "http://librarysimplified.org/terms/rel/user-profile"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/privacy.xml"),
            relation = "privacy-policy"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/shelf.xml"),
            relation = "http://opds-spec.org/shelf"
          ),
          Link.LinkBasic(
            href = URI("mailto:someone@example.com"),
            relation = "help"
          ),
          Link.LinkBasic(
            href = URI("http://www.example.com/logo.png"),
            relation = "logo"
          )
        )
      )

    Mockito.`when`(this.authDocumentParser.parse())
      .thenReturn(ParseResult.Success(listOf(), authDocument))

    val result =
      description.resolve { _, message -> this.logger.debug("{}", message) }

    this.logger.debug("result: {}", result)
    result as TaskResult.Failure
    Assertions.assertEquals("resolvingAuthDocumentNoStartURI", result.steps.last().resolution.message)
  }

  private fun <T> anyNotNull(): T {
    return Mockito.argThat<T> { x -> x != null }
  }
}
