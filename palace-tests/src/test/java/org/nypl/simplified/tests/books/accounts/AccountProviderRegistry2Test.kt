package org.nypl.simplified.tests.books.accounts

import android.content.Context
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.accounts.registry.AccountProviderRegistry2
import org.nypl.simplified.accounts.registry.AccountProviderRegistryConstants
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryRefresh
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.opds.auth_document.AuthenticationDocumentParsers
import org.nypl.simplified.tests.ExtraAssertions.assertInstanceOf
import org.nypl.simplified.tests.mocking.MockAccountProviderResolutionStrings
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionGetType
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySetting
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySettingsPutType
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.OffsetDateTime

class AccountProviderRegistry2Test {

  private lateinit var buildConfig: BuildConfigurationServiceType
  private lateinit var mockServer: MockWebServer
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var mockContext: Context
  private lateinit var accountProviders: List<AccountProvider>
  private lateinit var accountProvider: AccountProvider
  private lateinit var database: DBType
  private lateinit var databaseFile: Path
  private lateinit var directory: Path

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.mockContext = Mockito.mock()
    this.buildConfig = Mockito.mock(BuildConfigurationServiceType::class.java)
    this.directory = directory
    this.databaseFile = directory.resolve("database.db")
    this.database = DBFactory.open(
      DBParameters(
        file = this.databaseFile,
        accountProviderParsers = AccountProviderDescriptionCollectionParsers(),
        accountProviderSerializers = AccountProviderDescriptionCollectionSerializers()
      )
    )
    this.accountProviders =
      MockAccountProviders.fakeAccountProviderList()
    this.accountProvider =
      this.accountProviders[0]

    this.mockServer = MockWebServer()
    this.mockServer.start()

    this.httpClient =
      LSHTTPClients()
        .create(this.mockContext, LSHTTPClientConfiguration(
          applicationName = "org.thepalaceproject.palace.tests",
          applicationVersion = "1.0.0",
          networkAccess = LSHTTPNetworkAccess
        ))
  }

  @AfterEach
  fun tearDown() {
    this.database.close()
  }

  /**
   * Refreshing an empty registry results in only the given account provider descriptions
   * existing.
   */

  @Test
  fun testRefresh() {
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-0.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-1.json"))
    )

    val registry =
      AccountProviderRegistry2.create(
        buildConfig = this.buildConfig,
        database = this.database,
        mainExecutor = MoreExecutors.newDirectExecutorService(),
        defaultProvider = this.accountProvider,
        httpClient = this.httpClient,
        uriBase = URI.create("http://localhost:${this.mockServer.port}"),
        accountProviderResolutionStrings = MockAccountProviderResolutionStrings(),
        authDocumentParsers = AuthenticationDocumentParsers(),
        uiExecutor = MoreExecutors.directExecutor()
      )

    registry.refresh(
      AccountProviderRegistryRefresh.Full(
        clearBeforeRefresh = true,
        includeTestingLibraries = true
      )
    )

    val acc = registry.accountProviderDescriptionsSortedAttribute.get()
    assertEquals(21, acc.size)

    assertProviderDescriptionExists(URI.create("urn:uuid:b4ea88c4-0c42-4bf5-8d80-9513ddb8c5e4"))
    assertProviderDescriptionExists(URI.create("urn:uuid:6484ffbd-6668-4c90-970d-e623f710a91f"))
    assertProviderDescriptionExists(URI.create("urn:uuid:6a7c1df5-2985-4c1c-8f58-a701948f684b"))
    assertProviderDescriptionExists(URI.create("urn:uuid:723dbc56-2bd1-4f43-9a25-60d66474d6e2"))
    assertProviderDescriptionExists(URI.create("urn:uuid:56b3eac0-865f-4870-8785-77234003c676"))
    assertProviderDescriptionExists(URI.create("urn:uuid:3121bf14-c872-49c4-8315-8aa694595cbe"))
    assertProviderDescriptionExists(URI.create("urn:uuid:502cd632-aa03-43ce-8583-9d878f8b2373"))
    assertProviderDescriptionExists(URI.create("urn:uuid:bdc03fac-35dc-4051-8a70-680dd01ab196"))
    assertProviderDescriptionExists(URI.create("urn:uuid:0031aa77-5423-492b-82be-6ea68688e780"))
    assertProviderDescriptionExists(URI.create("urn:uuid:220db4e8-2ac1-448d-8459-a087317ad130"))
    assertProviderDescriptionExists(URI.create("urn:uuid:bfc4ab4c-8a48-4559-8655-a8ca88e10cfb"))
    assertProviderDescriptionExists(URI.create("urn:uuid:3d1fc497-faf5-4c2c-b4d8-e4bc10f166da"))
    assertProviderDescriptionExists(URI.create("urn:uuid:9cc9c5a7-b6e1-4ebc-a572-6756e7e4f35b"))
    assertProviderDescriptionExists(URI.create("urn:uuid:47cc1e34-9036-486f-948b-ab87b9257be3"))
    assertProviderDescriptionExists(URI.create("urn:uuid:8516fe35-a3a2-41dd-b5c4-eb48b569e3be"))
    assertProviderDescriptionExists(URI.create("urn:uuid:5cec0fce-d2a9-4181-9027-6419f2851f6b"))
    assertProviderDescriptionExists(URI.create("urn:uuid:6cb24a5b-d248-4440-b5aa-7ff19ba55e3c"))
    assertProviderDescriptionExists(URI.create("urn:uuid:61074b2d-f5e2-46d7-a245-7a0d2a7429cd"))
    assertProviderDescriptionExists(URI.create("urn:uuid:603d2cf0-9af3-41e0-9b43-ceb842addc53"))
    assertProviderDescriptionExists(URI.create("urn:uuid:045285ec-8031-4fd8-bd3e-9b94cee5f56e"))
  }

  /**
   * Refreshing the registry can result in account provider descriptions being removed.
   */

  @Test
  fun testRefreshRemoval() {
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-0.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-1.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-halfpage-0.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-1.json"))
    )

    val registry =
      AccountProviderRegistry2.create(
        buildConfig = this.buildConfig,
        database = this.database,
        mainExecutor = MoreExecutors.newDirectExecutorService(),
        defaultProvider = this.accountProvider,
        httpClient = this.httpClient,
        uriBase = URI.create("http://localhost:${this.mockServer.port}"),
        accountProviderResolutionStrings = MockAccountProviderResolutionStrings(),
        authDocumentParsers = AuthenticationDocumentParsers(),
        uiExecutor = MoreExecutors.directExecutor()
      )

    registry.refresh(
      AccountProviderRegistryRefresh.Full(
        clearBeforeRefresh = true,
        includeTestingLibraries = true
      )
    )

    val acc0 = registry.accountProviderDescriptionsSortedAttribute.get()
    assertEquals(21, acc0.size)

    registry.refresh(
      AccountProviderRegistryRefresh.Full(
        clearBeforeRefresh = true,
        includeTestingLibraries = true
      )
    )

    val acc1 = registry.accountProviderDescriptionsSortedAttribute.get()
    assertEquals(10, acc1.size)
  }

  /**
   * Refreshing the registry can result in account provider descriptions being removed.
   */

  @Test
  fun testRefreshFail0() {
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-0.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )

    val registry =
      AccountProviderRegistry2.create(
        buildConfig = this.buildConfig,
        database = this.database,
        mainExecutor = MoreExecutors.newDirectExecutorService(),
        defaultProvider = this.accountProvider,
        httpClient = this.httpClient,
        uriBase = URI.create("http://localhost:${this.mockServer.port}"),
        accountProviderResolutionStrings = MockAccountProviderResolutionStrings(),
        authDocumentParsers = AuthenticationDocumentParsers(),
        uiExecutor = MoreExecutors.directExecutor()
      )

    registry.refresh(
      AccountProviderRegistryRefresh.Full(
        clearBeforeRefresh = true,
        includeTestingLibraries = true
      )
    )

    assertInstanceOf(registry.status, AccountProviderRegistryStatus.Failed::class.java)
    assertEquals(21, registry.accountProviderDescriptions().size)
  }

  /**
   * Refreshing the registry can result in account provider descriptions being removed.
   */

  @Test
  fun testRefreshFail1() {
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-page-0.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )

    val registry =
      AccountProviderRegistry2.create(
        buildConfig = this.buildConfig,
        database = this.database,
        mainExecutor = MoreExecutors.newDirectExecutorService(),
        defaultProvider = this.accountProvider,
        httpClient = this.httpClient,
        uriBase = URI.create("http://localhost:${this.mockServer.port}"),
        accountProviderResolutionStrings = MockAccountProviderResolutionStrings(),
        authDocumentParsers = AuthenticationDocumentParsers(),
        uiExecutor = MoreExecutors.directExecutor()
      )

    registry.refresh(
      AccountProviderRegistryRefresh.Incremental(
        includeTestingLibraries = true
      )
    )

    assertInstanceOf(registry.status, AccountProviderRegistryStatus.Failed::class.java)
  }

  @Test
  fun testRefreshIncrementalStopEarly0() {
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(resourceText("registry-incremental-0.json"))
    )
    this.mockServer.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody("Failure!")
    )

    val registry =
      AccountProviderRegistry2.create(
        buildConfig = this.buildConfig,
        database = this.database,
        mainExecutor = MoreExecutors.newDirectExecutorService(),
        defaultProvider = this.accountProvider,
        httpClient = this.httpClient,
        uriBase = URI.create("http://localhost:${this.mockServer.port}"),
        accountProviderResolutionStrings = MockAccountProviderResolutionStrings(),
        authDocumentParsers = AuthenticationDocumentParsers(),
        uiExecutor = MoreExecutors.directExecutor()
      )

    this.database.openTransaction().use { t ->
      t.execute(
        DBQAccountRegistrySettingsPutType::class.java,
        DBQAccountRegistrySetting.TimeSetting(
          name = AccountProviderRegistryConstants.SETTING_LAST_UPDATE_NAME,
          value = OffsetDateTime.parse("2001-01-01T00:00:00+00:00")
        )
      )
      t.commit()
    }

    registry.refresh(
      AccountProviderRegistryRefresh.Incremental(
        includeTestingLibraries = true
      )
    )

    val idle: AccountProviderRegistryStatus.Idle =
      Assertions.assertInstanceOf(AccountProviderRegistryStatus.Idle::class.java, registry.status)

    assertEquals(3, idle.lastUpdateAffected)
  }

  private fun assertProviderDescriptionExists(id: URI) {
    Assertions.assertNotEquals(
      null,
      this.database.openTransaction().use { t ->
        t.execute(DBQAccountProviderDescriptionGetType::class.java, id)
      },
      "Provider $id must exist"
    )
  }

  private fun resourceText(
    name: String
  ): String {
    val path = "/org/nypl/simplified/tests/registry/$name"
    val stream = AccountProviderRegistry2Test::class.java.getResourceAsStream(path)
    return String(stream!!.readAllBytes(), StandardCharsets.UTF_8)
  }
}
