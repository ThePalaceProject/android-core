package org.nypl.simplified.tests.books.controller

import android.app.Application
import android.content.Context
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jfunctional.Option
import io.reactivex.subjects.PublishSubject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.accounts.api.AccountLogoutStringResourcesType
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.database.AccountBundledCredentialsEmpty
import org.nypl.simplified.accounts.database.AccountsDatabases
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.books.api.BookEvent
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.book_registry.BookPreviewRegistry
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatusEvent.BookStatusEventAdded
import org.nypl.simplified.books.borrowing.BorrowSubtasks
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskDirectoryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.books.controller.Controller
import org.nypl.simplified.books.controller.api.BookRevokeStringResourcesType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.links.Link
import org.nypl.simplified.notifications.NotificationTokenHTTPCalls
import org.nypl.simplified.notifications.NotificationTokenHTTPCallsType
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSParseException
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.ProfilesDatabases
import org.nypl.simplified.profiles.api.ProfileDatabaseException
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.profiles.controller.api.ProfileAccountCreationStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileAccountDeletionStringResourcesType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.EventAssertions
import org.nypl.simplified.tests.ExtraAssertions.assertInstanceOf
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.books.BookFormatsTesting
import org.nypl.simplified.tests.mocking.FakeAccountCredentialStorage
import org.nypl.simplified.tests.mocking.MockAccountCreationStringResources
import org.nypl.simplified.tests.mocking.MockAccountDeletionStringResources
import org.nypl.simplified.tests.mocking.MockAccountLoginStringResources
import org.nypl.simplified.tests.mocking.MockAccountLogoutStringResources
import org.nypl.simplified.tests.mocking.MockAccountProviderResolutionStrings
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.nypl.simplified.tests.mocking.MockAnalytics
import org.nypl.simplified.tests.mocking.MockBookFormatSupport
import org.nypl.simplified.tests.mocking.MockRevokeStringResources
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class BooksControllerContract {

  private val logger = LoggerFactory.getLogger(BooksControllerContract::class.java)

  private lateinit var accountEvents: PublishSubject<AccountEvent>
  private lateinit var accountEventsReceived: MutableList<AccountEvent>
  private lateinit var audioBookManifestStrategies: AudioBookManifestStrategiesType
  private lateinit var authDocumentParsers: AuthenticationDocumentParsersType
  private lateinit var bookEvents: MutableList<BookEvent>
  private lateinit var bookFormatSupport: MockBookFormatSupport
  private lateinit var bookPreviewRegistry: BookPreviewRegistryType
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var borrowSubtasks: BorrowSubtaskDirectoryType
  private lateinit var cacheDirectory: File
  private lateinit var contentResolver: ContentResolverType
  private lateinit var credentialsStore: FakeAccountCredentialStorage
  private lateinit var directoryDownloads: File
  private lateinit var directoryProfiles: File
  private lateinit var executorBooks: ListeningExecutorService
  private lateinit var executorDownloads: ListeningExecutorService
  private lateinit var executorFeeds: ListeningExecutorService
  private lateinit var executorNotifications: ListeningExecutorService
  private lateinit var executorTimer: ListeningExecutorService
  private lateinit var lsHTTP: LSHTTPClientType
  private lateinit var patronUserProfileParsers: PatronUserProfileParsersType
  private lateinit var profileEvents: PublishSubject<ProfileEvent>
  private lateinit var profileEventsReceived: MutableList<ProfileEvent>
  private lateinit var profiles: ProfilesDatabaseType
  private lateinit var server: MockWebServer

  protected abstract fun context(): Application

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

  private fun correctCredentials(): AccountAuthenticationCredentials {
    return AccountAuthenticationCredentials.Basic(
      userName = AccountUsername("abcd"),
      password = AccountPassword("1234"),
      adobeCredentials = null,
      authenticationDescription = null,
      annotationsURI = URI("https://www.example.com"),
      deviceRegistrationURI = URI("https://www.example.com")
    )
  }

  private fun createController(
    exec: ExecutorService,
    feedExecutor: ListeningExecutorService,
    accountEvents: PublishSubject<AccountEvent>,
    profileEvents: PublishSubject<ProfileEvent>,
    http: LSHTTPClientType,
    books: BookRegistryType,
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
    services.putService(LSHTTPClientType::class.java, this.lsHTTP)
    services.putService(NotificationTokenHTTPCallsType::class.java, NotificationTokenHTTPCalls(this.lsHTTP, this.executorNotifications))
    services.putService(OPDSFeedParserType::class.java, parser)
    services.putService(PatronUserProfileParsersType::class.java, patronUserProfileParsers)
    services.putService(ProfileAccountCreationStringResourcesType::class.java, profileAccountCreationStringResources)
    services.putService(ProfileAccountDeletionStringResourcesType::class.java, profileAccountDeletionStringResources)
    services.putService(ProfilesDatabaseType::class.java, profiles)

    return Controller.createFromServiceDirectory(
      application = this.context(),
      services = services,
      executorService = exec,
      accountEvents = accountEvents,
      profileEvents = profileEvents,
      cacheDirectory = this.cacheDirectory
    )
  }

  private fun createPreviewController(
    exec: ExecutorService,
    feedExecutor: ListeningExecutorService,
    accountEvents: PublishSubject<AccountEvent>,
    profileEvents: PublishSubject<ProfileEvent>,
    http: LSHTTPClientType,
    profiles: ProfilesDatabaseType,
    accountProviders: AccountProviderRegistryType,
    patronUserProfileParsers: PatronUserProfileParsersType
  ): BooksPreviewControllerType {
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
    services.putService(LSHTTPClientType::class.java, this.lsHTTP)
    services.putService(OPDSFeedParserType::class.java, parser)
    services.putService(NotificationTokenHTTPCallsType::class.java, NotificationTokenHTTPCalls(this.lsHTTP, this.executorNotifications))
    services.putService(PatronUserProfileParsersType::class.java, patronUserProfileParsers)
    services.putService(ProfileAccountCreationStringResourcesType::class.java, profileAccountCreationStringResources)
    services.putService(ProfileAccountDeletionStringResourcesType::class.java, profileAccountDeletionStringResources)
    services.putService(ProfilesDatabaseType::class.java, profiles)

    return Controller.createFromServiceDirectory(
      application = this.context(),
      services = services,
      executorService = exec,
      accountEvents = accountEvents,
      profileEvents = profileEvents,
      cacheDirectory = this.cacheDirectory
    )
  }

  @BeforeEach
  @Throws(Exception::class)
  fun setUp() {
    this.accountEvents = PublishSubject.create<AccountEvent>()
    this.accountEventsReceived = Collections.synchronizedList(ArrayList())
    this.audioBookManifestStrategies = Mockito.mock(AudioBookManifestStrategiesType::class.java)
    this.authDocumentParsers = Mockito.mock(AuthenticationDocumentParsersType::class.java)
    this.bookEvents = Collections.synchronizedList(ArrayList())
    this.bookFormatSupport = MockBookFormatSupport()
    this.bookPreviewRegistry = BookPreviewRegistry(DirectoryUtilities.directoryCreateTemporary())
    this.bookRegistry = BookRegistry.create()
    this.borrowSubtasks = BorrowSubtasks.directory()
    this.cacheDirectory = File.createTempFile("book-borrow-tmp", "dir")
    this.cacheDirectory.delete()
    this.cacheDirectory.mkdirs()
    this.contentResolver = Mockito.mock(ContentResolverType::class.java)
    this.credentialsStore = FakeAccountCredentialStorage()
    this.directoryDownloads = DirectoryUtilities.directoryCreateTemporary()
    this.directoryProfiles = DirectoryUtilities.directoryCreateTemporary()
    this.executorBooks = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.executorDownloads = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.executorFeeds = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.executorNotifications = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.executorTimer = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    this.patronUserProfileParsers = Mockito.mock(PatronUserProfileParsersType::class.java)
    this.profileEvents = PublishSubject.create<ProfileEvent>()
    this.profileEventsReceived = Collections.synchronizedList(ArrayList())
    this.profiles = profilesDatabaseWithoutAnonymous(this.accountEvents, this.directoryProfiles)

    this.lsHTTP =
      LSHTTPClients()
        .create(
          context = Mockito.mock(Context::class.java),
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-test",
            applicationVersion = "1.0.0",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    this.server = MockWebServer()
    this.server.start(port = 9000)
  }

  @AfterEach
  @Throws(Exception::class)
  fun tearDown() {
    this.executorBooks.shutdown()
    this.executorFeeds.shutdown()
    this.executorDownloads.shutdown()
    this.executorTimer.shutdown()
    this.server.close()
  }

  /**
   * If the remote side returns a non 401 error code, syncing should fail with an IO exception.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksSyncRemoteNon401() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accountsByProvider()[provider.id]!!
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setBody("")
    )

    val result = controller.booksSync(account.id).get()
    Assertions.assertTrue(result is TaskResult.Failure)
    Assertions.assertEquals(IOException::class.java, (result as TaskResult.Failure).exception!!.javaClass)
  }

  /**
   * If the provider does not support authentication, then syncing is impossible and does nothing.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksSyncWithoutAuthSupport() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeProvider(
        providerId = "urn:fake:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accountsByProvider()[provider.id]!!
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    Assertions.assertEquals(AccountLoggedIn(correctCredentials()), account.loginState)
    controller.booksSync(account.id).get()
    Assertions.assertEquals(AccountLoggedIn(correctCredentials()), account.loginState)
  }

  /**
   * If the remote side requires authentication but no credentials were provided, nothing happens.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksSyncMissingCredentials() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accountsByProvider()[provider.id]!!

    Assertions.assertEquals(AccountNotLoggedIn, account.loginState)
    controller.booksSync(account.id).get()
    Assertions.assertEquals(AccountNotLoggedIn, account.loginState)
  }

  /**
   * If the remote side returns garbage for a feed, an error is raised.
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  fun testBooksSyncBadFeed() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accountsByProvider()[provider.id]!!
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("Unlikely!")
    )

    val result = controller.booksSync(account.id).get()
    Assertions.assertTrue(result is TaskResult.Failure)
    Assertions.assertEquals(OPDSParseException::class.java, (result as TaskResult.Failure).exception!!.javaClass)
  }

  /**
   * If the remote side returns books the account doesn't have, new database entries are created.
   *
   * @throws Exception On errors
   */

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
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accountsByProvider()[provider.id]!!
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksSyncNewEntries.xml")))
    )

    this.bookRegistry.bookEvents().subscribe({ this.bookEvents.add(it) })

    Assertions.assertEquals(0L, this.bookRegistry.books().size.toLong())
    controller.booksSync(account.id).get()
    Assertions.assertEquals(3L, this.bookRegistry.books().size.toLong())

    this.bookRegistry.bookOrException(
      BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")
    )
    this.bookRegistry.bookOrException(
      BookID.create("f9a7536a61caa60f870b3fbe9d4304b2d59ea03c71cbaee82609e3779d1e6e0f")
    )
    this.bookRegistry.bookOrException(
      BookID.create("251cc5f69cd2a329bb6074b47a26062e59f5bb01d09d14626f41073f63690113")
    )

    EventAssertions.isType(
      BookStatusEventAdded::class.java,
      this.bookEvents,
      0
    )
    EventAssertions.isType(
      BookStatusEventAdded::class.java,
      this.bookEvents,
      1
    )
    EventAssertions.isType(
      BookStatusEventAdded::class.java,
      this.bookEvents,
      2
    )
  }

  /**
   * If the remote side returns few books than the account has, database entries are removed.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksSyncRemoveEntries() {
    this.bookFormatSupport.onIsSupportedFinalContentType = { true }
    this.bookFormatSupport.onIsSupportedPath = { true }

    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accountsByProvider()[provider.id]!!
    account.setLoginState(AccountLoggedIn(correctCredentials()))
    val bookDatabase = account.bookDatabase

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    /*
     * Populate the database by syncing against a feed that contains books.
     */

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksSyncNewEntries.xml")))
    )

    controller.booksSync(account.id).get()

    this.bookRegistry.bookOrException(
      BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")
    ).status as BookStatus.Loaned.LoanedNotDownloaded
    this.bookRegistry.bookOrException(
      BookID.create("f9a7536a61caa60f870b3fbe9d4304b2d59ea03c71cbaee82609e3779d1e6e0f")
    ).status as BookStatus.Loaned.LoanedNotDownloaded
    this.bookRegistry.bookOrException(
      BookID.create("251cc5f69cd2a329bb6074b47a26062e59f5bb01d09d14626f41073f63690113")
    ).status as BookStatus.Loaned.LoanedNotDownloaded

    this.bookRegistry.bookEvents().subscribe { this.bookEvents.add(it) }

    /*
     * Now run the sync again but this time with a feed that removes books.
     */

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksSyncRemoveEntries.xml")))
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBook0.xml")))
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBook1.xml")))
    )
    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBook2.xml")))
    )

    controller.booksSync(account.id).get()

    /*
     * Wait for all subtasks to complete.
     */

    this.executorBooks.shutdown()
    this.executorBooks.awaitTermination(30L, TimeUnit.SECONDS)

    Assertions.assertEquals(1, bookDatabase.books().size)

    bookDatabase.entry(
      BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")
    )

    this.bookRegistry.bookOrException(
      BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")
    ).status as BookStatus.Loaned.LoanedNotDownloaded

    this.bookRegistry.bookOrException(
      BookID.create("f9a7536a61caa60f870b3fbe9d4304b2d59ea03c71cbaee82609e3779d1e6e0f")
    ).status as BookStatus.Loanable

    this.bookRegistry.bookOrException(
      BookID.create("251cc5f69cd2a329bb6074b47a26062e59f5bb01d09d14626f41073f63690113")
    ).status as BookStatus.Loanable
  }

  /**
   * Deleting a book works.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksDelete() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accounts().values.first()
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksDelete.xml")))
    )

    controller.booksSync(account.id).get()

    val bookId = BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")

    Assertions.assertFalse(
      this.bookRegistry.bookOrException(bookId)
        .book
        .isDownloaded,
      "Book must not have a saved EPUB file"
    )

    val result = controller.bookDelete(account.id, bookId).get()
    result as TaskResult.Success

    assertThrows<NoSuchElementException> { this.bookRegistry.bookOrException(bookId).status }
  }

  /**
   * Cancelling a book download works.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksCancelDownload() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accounts().values.first()
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksDelete.xml")))
    )

    val bookId = BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")
    val bookAcquisition =
      OPDSAcquisition(
        OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS,
        Link.LinkBasic(this.server.url("/book.epub").toUri()),
        StandardFormatNames.genericEPUBFiles,
        listOf(),
        mapOf()
      )
    val bookEntry =
      OPDSAcquisitionFeedEntry.newBuilder(
        "5b7ec7e5-b137-4a11-b2df-4378e63ffb25",
        "Example Book 0",
        DateTime.now(),
        OPDSAvailabilityOpenAccess[Option.none()]
      ).addAcquisition(bookAcquisition)
        .build()

    controller.bookBorrow(account.id, bookId, bookEntry).get()
    controller.bookCancelDownloadAndDelete(account.id, bookId).get()

    assertThrows<NoSuchElementException> { this.bookRegistry.bookOrException(bookId).status }
  }

  /**
   * Dismissing a failed revocation that didn't actually fail does nothing.
   *
   * @throws Exception On errors
   */

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  @Throws(Exception::class)
  fun testBooksRevokeDismissHasNotFailed() {
    val controller =
      createController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        books = this.bookRegistry,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val provider =
      MockAccountProviders.fakeAuthProvider(
        uri = "urn:fake-auth:0",
        host = this.server.hostName,
        port = this.server.port
      )

    val profile = this.profiles.createProfile(provider, "Kermit")
    this.profiles.setProfileCurrent(profile.id)
    val account = profile.accounts().values.first()
    account.setLoginState(AccountLoggedIn(correctCredentials()))

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().readFrom(resource("testBooksSyncNewEntries.xml")))
    )

    controller.booksSync(account.id).get()

    val bookId = BookID.create("39434e1c3ea5620fdcc2303c878da54cc421175eb09ce1a6709b54589eb8711f")

    val statusBefore = this.bookRegistry.bookOrException(bookId).status
    assertInstanceOf(statusBefore, BookStatus.Loaned.LoanedNotDownloaded::class.java)

    controller.bookRevokeFailedDismiss(account.id, bookId).get()

    val statusAfter = this.bookRegistry.bookOrException(bookId).status
    Assertions.assertEquals(statusBefore, statusAfter)
  }

  @Test
  @Timeout(value = 3L, unit = TimeUnit.SECONDS)
  fun testBooksPreviewSuccess() {
    val controller =
      createPreviewController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.simpleUserProfile())
    )

    this.server.enqueue(
      MockResponse()
        .setResponseCode(200)
    )

    val bookPreviewStatus = arrayListOf<BookPreviewStatus>()
    this.bookPreviewRegistry.observeBookPreviewStatus().subscribe {
      bookPreviewStatus.add(it)
    }
    val acquisition =
      OPDSAcquisition(
        OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS,
        Link.LinkBasic(this.server.url("/book.epub").toUri()),
        StandardFormatNames.genericEPUBFiles,
        listOf(),
        mapOf()
      )
    val previewAcquisition =
      OPDSPreviewAcquisition(
        this.server.url("/book.epub").toUri(),
        StandardFormatNames.genericEPUBFiles
      )
    val bookEntry =
      OPDSAcquisitionFeedEntry.newBuilder(
        "5b7ec7e5-b137-4a11-b2df-4378e63ffb25",
        "Example Book 0",
        DateTime.now(),
        OPDSAvailabilityOpenAccess[Option.none()]
      )
        .addAcquisition(acquisition)
        .addPreviewAcquisition(previewAcquisition)
        .build()

    controller.handleBookPreviewStatus(
      entry = FeedEntry.FeedEntryOPDS(
        accountID = AccountID.generate(),
        feedEntry = bookEntry
      )
    ).get()

    assertInstanceOf(
      bookPreviewStatus.removeAt(0),
      BookPreviewStatus.HasPreview.Downloading::class.java
    )
    assertInstanceOf(
      bookPreviewStatus.removeAt(0),
      BookPreviewStatus.HasPreview.Downloading::class.java
    )
    assertInstanceOf(
      bookPreviewStatus.removeAt(0),
      BookPreviewStatus.HasPreview.Downloading::class.java
    )
    assertInstanceOf(
      bookPreviewStatus.removeAt(0),
      BookPreviewStatus.HasPreview.Ready.BookPreview::class.java
    )

    Assertions.assertEquals(0, bookPreviewStatus.size)
  }

  @Test
  fun testBooksNoPreview() {
    val controller =
      createPreviewController(
        exec = this.executorBooks,
        feedExecutor = this.executorFeeds,
        accountEvents = this.accountEvents,
        profileEvents = this.profileEvents,
        http = this.lsHTTP,
        profiles = this.profiles,
        accountProviders = MockAccountProviders.fakeAccountProviders(),
        patronUserProfileParsers = this.patronUserProfileParsers
      )

    val bookPreviewStatus = arrayListOf<BookPreviewStatus>()
    this.bookPreviewRegistry.observeBookPreviewStatus().subscribe {
      bookPreviewStatus.add(it)
    }
    val acquisition =
      OPDSAcquisition(
        OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS,
        Link.LinkBasic(this.server.url("/book.epub").toUri()),
        StandardFormatNames.genericEPUBFiles,
        listOf(),
        mapOf()
      )

    val bookEntry =
      OPDSAcquisitionFeedEntry.newBuilder(
        "5b7ec7e5-b137-4a11-b2df-4378e63ffb25",
        "Example Book 0",
        DateTime.now(),
        OPDSAvailabilityOpenAccess[Option.none()]
      )
        .addAcquisition(acquisition)
        .build()

    controller.handleBookPreviewStatus(
      entry = FeedEntry.FeedEntryOPDS(
        accountID = AccountID.generate(),
        feedEntry = bookEntry
      )
    ).get()

    assertInstanceOf(
      bookPreviewStatus.removeAt(0),
      BookPreviewStatus.None::class.java
    )
    Assertions.assertEquals(0, bookPreviewStatus.size)
  }

  private fun resource(file: String): InputStream {
    return BooksControllerContract::class.java.getResourceAsStream(file)!!
  }

  @Throws(ProfileDatabaseException::class)
  private fun profilesDatabaseWithoutAnonymous(
    accountEvents: PublishSubject<AccountEvent>,
    dirProfiles: File
  ): ProfilesDatabaseType {
    return ProfilesDatabases.openWithAnonymousProfileDisabled(
      context = context(),
      analytics = this.analytics,
      accountEvents = accountEvents,
      accountProviders = MockAccountProviders.fakeAccountProviders(),
      accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
      accountCredentialsStore = this.credentialsStore,
      accountsDatabases = AccountsDatabases,
      bookFormatSupport = BookFormatsTesting.supportsEverything,
      directory = dirProfiles
    )
  }

  private fun simpleUserProfile(): String {
    return resource("/org/nypl/simplified/tests/patron/example-with-device.json")
      .readBytes()
      .decodeToString()
  }
}
