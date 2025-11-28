package org.nypl.simplified.tests.books.borrowing

import android.app.Application
import io.reactivex.subjects.PublishSubject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.librarysimplified.services.api.ServiceDirectory
import org.librarysimplified.services.api.ServiceDirectoryType
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentialsStoreType
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.AccountAuthenticationCredentialsStore
import org.nypl.simplified.accounts.database.AccountBundledCredentialsEmpty
import org.nypl.simplified.accounts.database.AccountsDatabases
import org.nypl.simplified.books.audio.AudioBookManifests
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.BorrowRequest
import org.nypl.simplified.books.borrowing.BorrowRequirements
import org.nypl.simplified.books.borrowing.BorrowSubtasks
import org.nypl.simplified.books.borrowing.BorrowTask
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskDirectoryType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.profiles.ProfilesDatabases
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.mocking.MockAccountProviderRegistry
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.nypl.simplified.tests.mocking.MockAdobeAdeptConnector
import org.nypl.simplified.tests.mocking.MockAdobeAdeptExecutor
import org.nypl.simplified.tests.mocking.MockAdobeAdeptNetProvider
import org.nypl.simplified.tests.mocking.MockAdobeAdeptResourceProvider
import org.nypl.simplified.tests.mocking.MockAnalytics
import org.nypl.simplified.tests.mocking.MockBookFormatSupport
import org.nypl.simplified.tests.mocking.MockBundledContentResolver
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.nypl.simplified.tests.mocking.MockLCPService
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BugPP2325Test {

  private lateinit var webServer: MockWebServer
  private lateinit var subtasks: BorrowSubtaskDirectoryType
  private lateinit var accountCredentials: AccountAuthenticationCredentialsStoreType
  private lateinit var accountEvents: PublishSubject<AccountEvent>
  private lateinit var accountProvider: AccountProviderType
  private lateinit var accountProviderRegistry: MockAccountProviderRegistry
  private lateinit var adobeExecutor: MockAdobeAdeptExecutor
  private lateinit var adobeExecutorExec: ExecutorService
  private lateinit var analytics: MockAnalytics
  private lateinit var androidContext: Application
  private lateinit var bookFormatSupport: MockBookFormatSupport
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bundledContent: MockBundledContentResolver
  private lateinit var contentResolver: MockContentResolver
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var lcpService: MockLCPService
  private lateinit var profiles: ProfilesDatabaseType
  private lateinit var services: ServiceDirectoryType

  @BeforeEach
  fun setup(
    @TempDir credentialsDir: Path,
    @TempDir profilesDir: Path
  ) {
    this.webServer = MockWebServer()
    this.webServer.start(port = 30000)

    this.androidContext =
      Mockito.mock(Application::class.java)

    this.adobeExecutorExec =
      Executors.newFixedThreadPool(1)
    this.adobeExecutor =
      MockAdobeAdeptExecutor(
        this.adobeExecutorExec,
        MockAdobeAdeptConnector(
          MockAdobeAdeptNetProvider(),
          MockAdobeAdeptResourceProvider()
        )
      )

    this.bookFormatSupport =
      MockBookFormatSupport()
    this.bookRegistry =
      BookRegistry.create()
    this.bundledContent =
      MockBundledContentResolver()
    this.contentResolver =
      MockContentResolver()
    this.httpClient =
      LSHTTPClients()
        .create(this.androidContext, LSHTTPClientConfiguration("test", "1.0.0"))
    this.lcpService =
      MockLCPService(this.androidContext)
    this.services =
      ServiceDirectory.builder()
        .build()

    this.analytics =
      MockAnalytics()

    this.accountProvider =
      MockAccountProviders.fakeAccountProviderList()[0]
    this.accountProviderRegistry =
      MockAccountProviderRegistry.singleton(this.accountProvider)
    this.accountCredentials =
      AccountAuthenticationCredentialsStore.open(
        credentialsDir.resolve("credentials").toFile(),
        credentialsDir.resolve("credentialsTemp").toFile()
      )

    this.accountEvents =
      PublishSubject.create()

    this.profiles =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        this.androidContext,
        this.analytics,
        this.accountEvents,
        this.accountProviderRegistry,
        AccountBundledCredentialsEmpty.getInstance(),
        this.accountCredentials,
        AccountsDatabases,
        this.bookFormatSupport,
        profilesDir.toFile()
      )

    this.subtasks =
      BorrowSubtasks.directory()
  }

  @AfterEach
  fun tearDown() {
    this.adobeExecutorExec.shutdown()
    this.webServer.close()
  }

  @Test
  fun test(
    @TempDir cacheDirectory: Path,
    @TempDir temporaryDirectory: Path
  ) {
    val profile =
      this.profiles.currentProfileUnsafe()
    val account =
      profile.mostRecentAccount()

    val request =
      BorrowRequest.Start(
        accountId = account.id,
        profileId = profile.id,
        opdsAcquisitionFeedEntry = entryStart(),
        samlDownloadContext = null
      )

    val task =
      BorrowTask.createBorrowTask(
        requirements = BorrowRequirements(
          application = this.androidContext,
          adobeExecutor = this.adobeExecutor,
          audioBookManifestStrategies = AudioBookManifests,
          boundlessService = null,
          bookFormatSupport = this.bookFormatSupport,
          bookRegistry = this.bookRegistry,
          bundledContent = this.bundledContent,
          cacheDirectory = cacheDirectory.toFile(),
          clock = { Instant.now() },
          contentResolver = this.contentResolver,
          httpClient = this.httpClient,
          lcpService = this.lcpService,
          profiles = this.profiles,
          services = this.services,
          subtasks = this.subtasks,
          temporaryDirectory = temporaryDirectory.toFile()
        ),
        request = request
      )

    /*
     * Enqueue the feed response for /borrow.
     */

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(feed1EntryText)
    )

    /*
     * Enqueue some random bytes for /fulfill
     */

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/epub+zip")
        .setHeader("Content-Length", 5)
        .setBody("hello")
    )

    val r = task.execute() as TaskResult.Success

    run {
      assertEquals(2, this.webServer.requestCount)
    }
  }

  /**
   * The entry starts out with a set of indirect acquisitions such that the application will:
   *
   * 1. Create a loan.
   * 2. Get an ACSM file.
   * 3. Use the ACSM file to fulfill an EPUB.
   *
   * However, the acquisitions will [change](#feed1Text) when the loan is created...
   */

  val feed0Text = """
<feed 
    xmlns:simplified="http://librarysimplified.org/terms/" 
    xmlns:dcterms="http://purl.org/dc/terms/"
    xmlns:opds="http://opds-spec.org/2010/catalog" 
    xmlns:schema="http://schema.org/" 
    xmlns="http://www.w3.org/2005/Atom">
  <id>urn:uuid:e914674c-0567-4493-99c2-fda8121d1f55</id>
  <title>Imaginary Books</title>
  <updated>2015-05-12T20:17:38Z</updated>
  <link href="http://example.com" rel="self" />
  <entry>
    <id>urn:uuid:a363983b-bc58-49fa-aa92-a6866e11e191</id>
    <title>Something</title>
    <updated>2015-05-12T20:17:38Z</updated>
    <link href="http://127.0.0.1:30000/borrow" rel="http://opds-spec.org/acquisition/borrow" type="application/atom+xml;type=entry;profile=opds-catalog">
      <opds:indirectAcquisition type="application/vnd.adobe.adept+xml">
        <opds:indirectAcquisition type="application/epub+zip"/>
      </opds:indirectAcquisition>
      <opds:indirectAcquisition type="application/atom+xml;type=entry;profile=opds-catalog">
        <opds:indirectAcquisition type="text/html;profile=&quot;http://librarysimplified.org/terms/profiles/streaming-media&quot;"/>
      </opds:indirectAcquisition>
      <opds:availability status="available"/>
      <opds:holds total="0"/>
      <opds:copies total="999999" available="999999"/>
    </link>
  </entry>
</feed>
  """.trimIndent()

  /**
   * The entry _now_ says that we can get an EPUB file directly, whereas previously we said
   * we were going to get an ACSM file first.
   */

  val feed1EntryText = """
  <entry
    xmlns:simplified="http://librarysimplified.org/terms/" 
    xmlns:dcterms="http://purl.org/dc/terms/"
    xmlns:opds="http://opds-spec.org/2010/catalog" 
    xmlns:schema="http://schema.org/" 
    xmlns="http://www.w3.org/2005/Atom">
    <id>urn:uuid:a363983b-bc58-49fa-aa92-a6866e11e191</id>
    <title>Something</title>
    <updated>2015-05-12T20:17:38Z</updated>
    <link href="http://127.0.0.1:30000/fulfill" rel="http://opds-spec.org/acquisition" type="application/epub+zip">
      <opds:availability status="available" since="2025-03-25T15:45:19+00:00" until="2025-03-26T15:43:33+00:00"/>
      <opds:holds total="0"/>
      <opds:copies total="999999" available="999999"/>
    </link>
  </entry>
  """.trimIndent()

  val feed1Text = """
<feed 
    xmlns:simplified="http://librarysimplified.org/terms/" 
    xmlns:dcterms="http://purl.org/dc/terms/"
    xmlns:opds="http://opds-spec.org/2010/catalog" 
    xmlns:schema="http://schema.org/" 
    xmlns="http://www.w3.org/2005/Atom">
  <id>urn:uuid:e914674c-0567-4493-99c2-fda8121d1f55</id>
  <title>Imaginary Books</title>
  <updated>2015-05-12T20:17:38Z</updated>
  <link href="http://example.com" rel="self" />
  $feed1EntryText
</feed>
  """.trimIndent()

  private fun entryStart(): OPDSAcquisitionFeedEntry {
    val parser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
    val data =
      ByteArrayInputStream(feed0Text.toByteArray())
    val feed =
      parser.parse(URI.create("urn:feed0"), data)

    return feed.feedEntries[0]
  }
}
