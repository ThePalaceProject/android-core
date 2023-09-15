package org.nypl.simplified.tests.http.refresh_token.sync

import android.content.Context
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jfunctional.Option
import io.reactivex.subjects.PublishSubject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
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
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationTokenInfo
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.accounts.api.AccountLogoutStringResourcesType
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.book_registry.BookPreviewRegistry
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.BorrowSubtasks
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskDirectoryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.books.controller.Controller
import org.nypl.simplified.books.controller.api.BookRevokeStringResourcesType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.notifications.NotificationTokenHTTPCalls
import org.nypl.simplified.notifications.NotificationTokenHTTPCallsType
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.profiles.api.idle_timer.ProfileIdleTimerType
import org.nypl.simplified.profiles.controller.api.ProfileAccountCreationStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileAccountDeletionStringResourcesType
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.books.controller.BooksControllerContract
import org.nypl.simplified.tests.books.idle_timer.InoperableIdleTimer
import org.nypl.simplified.tests.mocking.FakeAccountCredentialStorage
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockAccountCreationStringResources
import org.nypl.simplified.tests.mocking.MockAccountDeletionStringResources
import org.nypl.simplified.tests.mocking.MockAccountLoginStringResources
import org.nypl.simplified.tests.mocking.MockAccountLogoutStringResources
import org.nypl.simplified.tests.mocking.MockAccountProviderResolutionStrings
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.nypl.simplified.tests.mocking.MockAnalytics
import org.nypl.simplified.tests.mocking.MockBookFormatSupport
import org.nypl.simplified.tests.mocking.MockRevokeStringResources
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SyncBookRefreshToken {
  private lateinit var account: MockAccount
  private lateinit var accountEvents: PublishSubject<AccountEvent>
  private lateinit var accountID: AccountID
  private lateinit var audioBookManifestStrategies: AudioBookManifestStrategiesType
  private lateinit var authDocumentParsers: AuthenticationDocumentParsersType
  private lateinit var bookFormatSupport: MockBookFormatSupport
  private lateinit var bookPreviewRegistry: BookPreviewRegistryType
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var borrowSubtasks: BorrowSubtaskDirectoryType
  private lateinit var cacheDirectory: File
  private lateinit var contentResolver: ContentResolverType
  private lateinit var credentialsStore: FakeAccountCredentialStorage
  private lateinit var directoryProfiles: File
  private lateinit var executorBooks: ListeningExecutorService
  private lateinit var executorDownloads: ListeningExecutorService
  private lateinit var executorFeeds: ListeningExecutorService
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var patronUserProfileParsers: PatronUserProfileParsersType
  private lateinit var profileEvents: PublishSubject<ProfileEvent>
  private lateinit var profileEventsReceived: MutableList<ProfileEvent>
  private lateinit var profiles: ProfilesDatabaseType
  private lateinit var webServer: MockWebServer

  private val accountProviderResolutionStrings =
    MockAccountProviderResolutionStrings()
  private val accountLoginStringResources =
    MockAccountLoginStringResources()
  private val accountLogoutStringResources =
    MockAccountLogoutStringResources()
  private val revokeStringResources =
    MockRevokeStringResources()
  private val profileAccountDeletionStringResources =
    MockAccountDeletionStringResources()
  private val profileAccountCreationStringResources =
    MockAccountCreationStringResources()
  private val analytics =
    MockAnalytics()

  @BeforeEach
  fun testSetup() {
    this.accountID =
      AccountID.generate()
    this.account = MockAccount(this.accountID)
    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.account.setAccountProvider(
      MockAccountProviders.fakeProvider(
        providerId = account.provider.id.toString(),
        host = webServer.hostName,
        port = webServer.port
      ).copy(
        authentication = this.account.provider.authentication
      )
    )

    val credentials = AccountAuthenticationCredentials.BasicToken(
      userName = AccountUsername("1234"),
      password = AccountPassword("5678"),
      authenticationTokenInfo = AccountAuthenticationTokenInfo(
        accessToken = "abcd",
        authURI = URI("https://www.authrefresh.com")
      ),
      adobeCredentials = null,
      authenticationDescription = "BasicToken",
      annotationsURI = URI("https://www.example.com")
    )

    this.account.setLoginState(AccountLoginState.AccountLoggedIn(credentials))
    this.accountEvents = PublishSubject.create()
    this.audioBookManifestStrategies = Mockito.mock(AudioBookManifestStrategiesType::class.java)
    this.authDocumentParsers = Mockito.mock(AuthenticationDocumentParsersType::class.java)
    this.bookFormatSupport = MockBookFormatSupport()
    this.bookRegistry =
      BookRegistry.create()
    this.bookPreviewRegistry = BookPreviewRegistry(DirectoryUtilities.directoryCreateTemporary())
    this.borrowSubtasks = BorrowSubtasks.directory()
    this.cacheDirectory = File.createTempFile("book-borrow-tmp", "dir")
    this.cacheDirectory.delete()
    this.cacheDirectory.mkdirs()
    this.contentResolver = Mockito.mock(ContentResolverType::class.java)
    this.credentialsStore = FakeAccountCredentialStorage()
    this.directoryProfiles = DirectoryUtilities.directoryCreateTemporary()
    this.executorBooks = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.executorDownloads = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.executorFeeds = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.patronUserProfileParsers = Mockito.mock(PatronUserProfileParsersType::class.java)
    this.profileEvents = PublishSubject.create()
    this.profileEventsReceived = Collections.synchronizedList(ArrayList())
    this.profiles = Mockito.mock(ProfilesDatabaseType::class.java)

    this.httpClient =
      LSHTTPClients()
        .create(
          context = Mockito.mock(Context::class.java),
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
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksSyncNewEntries() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.httpClient,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val profile =
      Mockito.mock(ProfileType::class.java)
    val profileId =
      Mockito.mock(ProfileID::class.java)

    val profilesMap = sortedMapOf(profileId to profile)

    Mockito.`when`(profile.id).thenReturn(profileId)
    Mockito.`when`(profiles.profiles()).thenReturn(profilesMap)
    Mockito.`when`(profiles.currentProfile()).thenReturn(Option.some(profile))
    Mockito.`when`(profiles.currentProfileUnsafe()).thenReturn(profile)
    Mockito.`when`(profile.account(accountID))
      .thenReturn(account)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(this.simpleUserProfile())
    )

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksSyncNewEntries.xml")))
    )

    controller.booksSync(account.id).get()

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  private fun createController(
    exec: ExecutorService,
    feedExecutor: ListeningExecutorService,
    accountEvents: PublishSubject<AccountEvent>,
    profileEvents: PublishSubject<ProfileEvent>,
    http: LSHTTPClientType,
    profiles: ProfilesDatabaseType,
    accountProviders: AccountProviderRegistryType,
    patronUserProfileParsers: PatronUserProfileParsersType
  ): BooksControllerType {
    val parser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
    val transport =
      FeedHTTPTransport(http)

    val bundledContent =
      BundledContentResolverType { uri -> throw FileNotFoundException(uri.toString()) }

    val feedLoader =
      FeedLoader.create(
        bookFormatSupport = this.bookFormatSupport,
        bundledContent = bundledContent,
        contentResolver = this.contentResolver,
        exec = feedExecutor,
        parser = parser,
        searchParser = OPDSSearchParser.newParser(),
        transport = transport
      )

    val services = MutableServiceDirectory()
    services.putService(AccountLoginStringResourcesType::class.java, this.accountLoginStringResources)
    services.putService(AccountLogoutStringResourcesType::class.java, this.accountLogoutStringResources)
    services.putService(AccountProviderRegistryType::class.java, accountProviders)
    services.putService(AccountProviderResolutionStringsType::class.java, this.accountProviderResolutionStrings)
    services.putService(AnalyticsType::class.java, this.analytics)
    services.putService(AudioBookManifestStrategiesType::class.java, this.audioBookManifestStrategies)
    services.putService(AuthenticationDocumentParsersType::class.java, this.authDocumentParsers)
    services.putService(BookFormatSupportType::class.java, this.bookFormatSupport)
    services.putService(BookRegistryType::class.java, this.bookRegistry)
    services.putService(BookPreviewRegistryType::class.java, this.bookPreviewRegistry)
    services.putService(BorrowSubtaskDirectoryType::class.java, this.borrowSubtasks)
    services.putService(BookRevokeStringResourcesType::class.java, revokeStringResources)
    services.putService(BundledContentResolverType::class.java, bundledContent)
    services.putService(ContentResolverType::class.java, this.contentResolver)
    services.putService(FeedLoaderType::class.java, feedLoader)
    services.putService(LSHTTPClientType::class.java, this.httpClient)
    services.putService(NotificationTokenHTTPCallsType::class.java, NotificationTokenHTTPCalls(this.httpClient))
    services.putService(OPDSFeedParserType::class.java, parser)
    services.putService(PatronUserProfileParsersType::class.java, patronUserProfileParsers)
    services.putService(ProfileAccountCreationStringResourcesType::class.java, profileAccountCreationStringResources)
    services.putService(ProfileAccountDeletionStringResourcesType::class.java, profileAccountDeletionStringResources)
    services.putService(ProfileIdleTimerType::class.java, InoperableIdleTimer())
    services.putService(ProfilesDatabaseType::class.java, profiles)

    return Controller.createFromServiceDirectory(
      services = services,
      executorService = exec,
      accountEvents = accountEvents,
      profileEvents = profileEvents,
      cacheDirectory = this.cacheDirectory
    )
  }

  private fun resource(file: String): InputStream {
    return BooksControllerContract::class.java.getResourceAsStream(file)!!
  }

  private fun simpleUserProfile(): String {
    return resource("/org/nypl/simplified/tests/patron/example-with-device.json")
      .readBytes()
      .decodeToString()
  }
}
