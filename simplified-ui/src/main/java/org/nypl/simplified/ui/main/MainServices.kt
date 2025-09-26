package org.nypl.simplified.ui.main

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.AssetManager
import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.picasso.Picasso
import io.reactivex.subjects.PublishSubject
import org.joda.time.LocalDateTime
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.documents.DocumentConfigurationServiceType
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.documents.DocumentStores
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.reports.Reports
import org.librarysimplified.services.api.ServiceDirectory
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.BuildConfig
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.simplified.accessibility.AccessibilityService
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentialsStoreType
import org.nypl.simplified.accounts.api.AccountBundledCredentialsType
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.accounts.api.AccountLogoutStringResourcesType
import org.nypl.simplified.accounts.api.AccountProviderFallbackType
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.AccountAuthenticationCredentialsStore
import org.nypl.simplified.accounts.database.AccountBundledCredentialsEmpty
import org.nypl.simplified.accounts.database.AccountsDatabases
import org.nypl.simplified.accounts.json.AccountBundledCredentialsJSON
import org.nypl.simplified.accounts.registry.AccountProviderRegistry
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryDebugging
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceResolutionStrings
import org.nypl.simplified.adobe.extensions.AdobeConfigurationServiceType
import org.nypl.simplified.adobe.extensions.AdobeDRMServices
import org.nypl.simplified.analytics.api.Analytics
import org.nypl.simplified.analytics.api.AnalyticsConfiguration
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.bookmarks.BookmarkService
import org.nypl.simplified.bookmarks.api.BookmarkServiceProviderType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.internal.BHTTPCalls
import org.nypl.simplified.books.audio.AudioBookFeedbooksSecretServiceType
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.audio.AudioBookManifests
import org.nypl.simplified.books.audio.AudioBookOverdriveSecretServiceType
import org.nypl.simplified.books.book_registry.BookPreviewRegistry
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.BorrowSubtasks
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskDirectoryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.books.controller.Controller
import org.nypl.simplified.books.controller.api.BookRevokeStringResourcesType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.nypl.simplified.books.covers.BookCoverBadgeLookupType
import org.nypl.simplified.books.covers.BookCoverGenerator
import org.nypl.simplified.books.covers.BookCoverGeneratorType
import org.nypl.simplified.books.covers.BookCoverProvider
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.time.tracking.TimeTrackingHTTPCalls
import org.nypl.simplified.books.time.tracking.TimeTrackingService
import org.nypl.simplified.books.time.tracking.TimeTrackingServiceType
import org.nypl.simplified.boot.api.BootEvent
import org.nypl.simplified.boot.api.BootFailureTesting
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.content.api.ContentResolverSane
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.crashlytics.api.CrashlyticsServiceType
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.notifications.NotificationTokenHTTPCalls
import org.nypl.simplified.notifications.NotificationTokenHTTPCallsType
import org.nypl.simplified.notifications.NotificationsService
import org.nypl.simplified.notifications.NotificationsServiceType
import org.nypl.simplified.opds.auth_document.AuthenticationDocumentParsers
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.patron.PatronUserProfileParsers
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.ProfilesDatabases
import org.nypl.simplified.profiles.api.ProfileDatabaseException
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.profiles.controller.api.ProfileAccountCreationStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileAccountDeletionStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.tenprint.TenPrintGenerator
import org.nypl.simplified.tenprint.TenPrintGeneratorType
import org.nypl.simplified.threads.NamedThreadPools
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountEvents
import org.nypl.simplified.ui.catalog.CatalogBookRegistryEvents
import org.nypl.simplified.ui.catalog.CatalogCoverBadgeImages
import org.nypl.simplified.ui.catalog.CatalogOPDSClients
import org.nypl.simplified.ui.images.ImageAccountIconRequestHandler
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.screen.ScreenSizeInformation
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.nypl.simplified.ui.settings.SettingsProfileEvents
import org.readium.r2.lcp.LcpService
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClient
import org.thepalaceproject.opds.client.OPDSClientParameters
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.ServiceLoader

internal object MainServices {

  private val logger = LoggerFactory.getLogger(MainServices::class.java)

  /**
   * The current on-disk data version. The entire directory tree the application uses
   * to store data is versioned in order to make it easier to migrate data to new versions
   * at a later date.
   *
   * It's important that this version number begins with a letter: Old version of the software
   * stored individual accounts in numbered directories, and we want to avoid any possibility
   * of migration code thinking that this directory is an old account just because the name
   * happens to parse as an integer.
   */

  const val CURRENT_DATA_VERSION = "v4.0"

  private data class Directories(
    val directoryStorageBaseVersioned: File,
    val directoryStorageDownloads: File,
    val directoryStorageDocuments: File,
    val directoryStorageProfiles: File,
    val directoryStorageTimeTrackingSender: File,
    val directoryStorageTimeTrackingCollector: File,
    val directoryStorageTimeTrackingDebug: File,
    val directoryStorageBoundless: File,
  )

  private fun initializeDirectories(context: Application): Directories {
    logger.debug("initializing directories")

    val directoryStorageBaseVersioned =
      File(context.filesDir, CURRENT_DATA_VERSION)
    val directoryStorageDownloads =
      File(directoryStorageBaseVersioned, "downloads")
    val directoryStorageDocuments =
      File(directoryStorageBaseVersioned, "documents")
    val directoryStorageProfiles =
      File(directoryStorageBaseVersioned, "profiles")
    val directoryStorageTimeTracking =
      File(directoryStorageBaseVersioned, "time_tracking")
    val directoryStorageTimeTrackingDebug =
      File(directoryStorageTimeTracking, "debug")
    val directoryStorageTimeTrackingSender =
      File(directoryStorageTimeTracking, "sender")
    val directoryStorageTimeTrackingCollector =
      File(directoryStorageTimeTracking, "collector")
    val directoryStorageBoundless =
      File(directoryStorageBaseVersioned, "boundless")

    logger.debug("directoryStorageBaseVersioned:         {}", directoryStorageBaseVersioned)
    logger.debug("directoryStorageDownloads:             {}", directoryStorageDownloads)
    logger.debug("directoryStorageDocuments:             {}", directoryStorageDocuments)
    logger.debug("directoryStorageProfiles:              {}", directoryStorageProfiles)
    logger.debug("directoryStorageTimeTracking:          {}", directoryStorageTimeTracking)
    logger.debug(
      "directoryStorageTimeTrackingDebug:     {}",
      directoryStorageTimeTrackingDebug
    )
    logger.debug(
      "directoryStorageTimeTrackingSender:    {}",
      directoryStorageTimeTrackingSender
    )
    logger.debug(
      "directoryStorageTimeTrackingCollector: {}",
      directoryStorageTimeTrackingCollector
    )
    logger.debug("directoryStorageBoundless:  {}", directoryStorageBoundless)

    /*
     * Make sure the required directories exist. There is no sane way to
     * recover if they cannot be created!
     */

    val directories =
      listOf(
        directoryStorageBaseVersioned,
        directoryStorageDownloads,
        directoryStorageDocuments,
        directoryStorageProfiles,
        directoryStorageTimeTracking,
        directoryStorageTimeTrackingSender,
        directoryStorageTimeTrackingCollector,
        directoryStorageTimeTrackingDebug,
        directoryStorageBoundless
      )

    var exception: Exception? = null
    for (directory in directories) {
      try {
        DirectoryUtilities.directoryCreate(directory)
      } catch (e: Exception) {
        if (exception == null) {
          exception = e
        } else {
          exception.addSuppressed(exception)
        }
      }
    }

    if (exception != null) {
      throw exception
    }

    return Directories(
      directoryStorageBaseVersioned = directoryStorageBaseVersioned,
      directoryStorageDownloads = directoryStorageDownloads,
      directoryStorageDocuments = directoryStorageDocuments,
      directoryStorageProfiles = directoryStorageProfiles,
      directoryStorageTimeTrackingDebug = directoryStorageTimeTrackingDebug,
      directoryStorageTimeTrackingSender = directoryStorageTimeTrackingSender,
      directoryStorageTimeTrackingCollector = directoryStorageTimeTrackingCollector,
      directoryStorageBoundless = directoryStorageBoundless
    )
  }

  private fun findAdobeConfiguration(): AdobeConfigurationServiceType {
    return object : AdobeConfigurationServiceType {
      override val packageOverride: String?
        get() = null

      override val debugLogging: Boolean
        get() = false

      override val dataDirectoryName: String
        get() = CURRENT_DATA_VERSION
    }
  }

  private fun createLocalImageLoader(context: Application): ImageLoaderType {
    val localImageLoader =
      Picasso.Builder(context)
        .indicatorsEnabled(false)
        .loggingEnabled(true)
        .addRequestHandler(ImageAccountIconRequestHandler(context))
        .build()

    return object : ImageLoaderType {
      override val loader: Picasso
        get() = localImageLoader
    }
  }

  private fun loadDefaultAccountProvider(): AccountProviderType {
    val providers =
      ServiceLoader.load(AccountProviderFallbackType::class.java)
        .map { provider -> provider.get() }
        .toList()

    if (providers.isEmpty()) {
      throw java.lang.IllegalStateException("No fallback account providers available!")
    }
    return providers.first()
  }

  private fun createAccountProviderRegistry(
    context: Application,
    http: LSHTTPClientType
  ): AccountProviderRegistryType {
    val defaultAccountProvider =
      loadDefaultAccountProvider()
    val accountProviders =
      AccountProviderRegistry.createFromServiceLoader(context, http, defaultAccountProvider)
    for (id in accountProviders.accountProviderDescriptions().keys) {
      logger.debug("loaded account provider: {}", id)
    }
    return accountProviders
  }

  private fun createAccountAuthenticationCredentialsStore(
    directories: Directories
  ): AccountAuthenticationCredentialsStoreType {
    val accountCredentialsStore = try {
      val credentials =
        File(directories.directoryStorageBaseVersioned, "credentials.json")
      val credentialsTemp =
        File(directories.directoryStorageBaseVersioned, "credentials.json.tmp")

      logger.debug("credentials store path: {}", credentials)
      AccountAuthenticationCredentialsStore.open(credentials, credentialsTemp)
    } catch (e: Exception) {
      logger.debug("could not initialize credentials store: ", e)
      throw IllegalStateException("could not initialize credentials store", e)
    }
    logger.debug("credentials loaded: {}", accountCredentialsStore.size())
    return accountCredentialsStore
  }

  @Throws(IOException::class)
  private fun createBundledCredentials(assets: AssetManager): AccountBundledCredentialsType {
    return assets.open("account_bundled_credentials.json").use { stream ->
      AccountBundledCredentialsJSON.deserializeFromStream(ObjectMapper(), stream)
    }
  }

  private fun createAccountBundledCredentials(
    context: Application
  ): AccountBundledCredentialsType {
    return try {
      createBundledCredentials(context.assets)
    } catch (e: FileNotFoundException) {
      logger.debug("could not initialize bundled credentials; none found")
      AccountBundledCredentialsEmpty.getInstance()
    } catch (e: IOException) {
      logger.debug("could not initialize bundled credentials: ", e)
      throw IllegalStateException("could not initialize bundled credentials", e)
    }
  }

  @Throws(ProfileDatabaseException::class)
  private fun createProfileDatabase(
    context: Application,
    analytics: AnalyticsType,
    accountEvents: PublishSubject<AccountEvent>,
    accountProviders: AccountProviderRegistryType,
    accountBundledCredentials: AccountBundledCredentialsType,
    accountCredentialsStore: AccountAuthenticationCredentialsStoreType,
    bookFormatSupport: BookFormatSupportType,
    directory: File
  ): ProfilesDatabaseType {
    this.logger.debug("opening profile database with anonymous profile")
    return ProfilesDatabases.openWithAnonymousProfileEnabled(
      context = context,
      analytics = analytics,
      accountEvents = accountEvents,
      accountProviders = accountProviders,
      accountBundledCredentials = accountBundledCredentials,
      accountCredentialsStore = accountCredentialsStore,
      accountsDatabases = AccountsDatabases,
      bookFormatSupport = bookFormatSupport,
      directory = directory
    )
  }

  private fun createFeedLoader(
    http: LSHTTPClientType,
    opdsFeedParser: OPDSFeedParserType,
    bookFormatSupport: BookFormatSupportType,
    bundledContent: BundledContentResolverType,
    contentResolver: ContentResolverType
  ): FeedLoaderType {
    val execCatalogFeeds =
      NamedThreadPools.namedThreadPool(1, "catalog-feed", 19)
    val feedSearchParser =
      OPDSSearchParser.newParser()
    val feedTransport =
      FeedHTTPTransport(http)

    return FeedLoader.create(
      bookFormatSupport = bookFormatSupport,
      bundledContent = bundledContent,
      contentResolver = contentResolver,
      exec = execCatalogFeeds,
      parser = opdsFeedParser,
      searchParser = feedSearchParser,
      transport = feedTransport
    )
  }

  private fun createFeedParser(): OPDSFeedParserType {
    return OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
  }

  private fun <T : Any> optionalFromServiceLoader(interfaceType: Class<T>): T? {
    return ServiceLoader.load(interfaceType)
      .toList()
      .firstOrNull()
  }

  private fun createBookmarksService(
    http: LSHTTPClientType,
    bookController: ProfilesControllerType
  ): BookmarkServiceType {
    val threadFactory: (Runnable) -> Thread = { runnable ->
      NamedThreadPools.namedThreadPoolFactory("bookmarks", 19).newThread(runnable)
    }

    val service =
      BookmarkService.createService(
        BookmarkServiceProviderType.Requirements(
          threads = threadFactory,
          events = PublishSubject.create(),
          httpCalls = BHTTPCalls(ObjectMapper(), http),
          profilesController = bookController
        )
      )

    service.bookmarkLoadAll()
    return service
  }

  private fun createCoverProvider(
    context: Application,
    bookRegistry: BookRegistryReadableType,
    bundledContentResolver: BundledContentResolverType,
    coverGenerator: BookCoverGeneratorType,
    badgeLookup: BookCoverBadgeLookupType
  ): BookCoverProviderType {
    val execCovers =
      NamedThreadPools.namedThreadPool(2, "cover", 19)
    return BookCoverProvider.newCoverProvider(
      context = context,
      bookRegistry = bookRegistry,
      coverGenerator = coverGenerator,
      badgeLookup = badgeLookup,
      bundledContentResolver = bundledContentResolver,
      executor = execCovers,
      debugCacheIndicators = false,
      debugLogging = false
    )
  }

  private fun createBookCoverBadgeLookup(
    context: Application,
    screenSize: ScreenSizeInformationType
  ): BookCoverBadgeLookupType {
    return CatalogCoverBadgeImages.create(context.resources, screenSize)
  }

  private fun publishApplicationStartupEvent(
    context: Application,
    analytics: AnalyticsType
  ) {
    try {
      val packageInfo =
        context.packageManager.getPackageInfo(context.packageName, 0)

      val event =
        AnalyticsEvent.ApplicationOpened(
          LocalDateTime.now(),
          null,
          packageInfo.packageName,
          BuildConfig.SIMPLIFIED_VERSION,
          packageInfo.versionCode
        )

      analytics.publishEvent(event)
    } catch (e: PackageManager.NameNotFoundException) {
      logger.debug("could not get package info for analytics: ", e)
    }
  }

  private fun findBuildConfiguration(): BuildConfigurationServiceType {
    val existing =
      optionalFromServiceLoader(BuildConfigurationServiceType::class.java)

    if (existing != null) {
      return existing
    }

    throw IllegalStateException("Missing build configuration service")
  }

  fun setup(
    context: Application,
    onProgress: (BootEvent) -> Unit
  ): ServiceDirectoryType {
    fun publishEvent(message: String) {
      logger.debug("boot: {}", message)
      onProgress.invoke(BootEvent.BootInProgress(message))
    }

    BootFailureTesting.failBootProcessForTestingPurposesIfRequested(context)

    val services = ServiceDirectory.builder()
    val assets = context.assets
    val strings = MainServicesStrings(context.resources)

    Reports.reportAppVersion = BuildConfig.SIMPLIFIED_VERSION
    Reports.reportAppCommit = BuildConfig.SIMPLIFIED_GIT_COMMIT

    fun <T : Any> addService(
      message: String,
      interfaceType: Class<T>,
      serviceConstructor: () -> T
    ): T {
      publishEvent(message)
      val service = serviceConstructor.invoke()
      services.addService(interfaceType, service)
      return service
    }

    fun <T : Any> addServiceOptionally(
      message: String,
      interfaceType: Class<T>,
      serviceConstructor: () -> T?
    ): T? {
      publishEvent(message)
      val service = serviceConstructor.invoke()
      if (service != null) {
        services.addService(interfaceType, service)
      }
      return service
    }

    fun <T : Any> addServiceFromServiceLoaderOptionally(
      message: String,
      interfaceType: Class<T>
    ): T? {
      publishEvent(message)
      val service = ServiceLoader.load(interfaceType).firstOrNull()
      if (service != null) {
        services.addService(interfaceType, service)
      } else {
        logger.debug("no services of type {} available in ServiceLoader", interfaceType)
      }
      return service
    }

    addService(
      message = strings.bootingGeneral("login strings"),
      interfaceType = AccountLoginStringResourcesType::class.java,
      serviceConstructor = { MainLoginStringResources(context.resources) }
    )

    addService(
      message = strings.bootingGeneral("logout strings"),
      interfaceType = AccountLogoutStringResourcesType::class.java,
      serviceConstructor = { MainLogoutStringResources(context.resources) }
    )

    addService(
      message = strings.bootingGeneral("account resolution strings"),
      interfaceType = AccountProviderResolutionStringsType::class.java,
      serviceConstructor = {
        AccountProviderSourceResolutionStrings(context.resources)
      }
    )

    addService(
      message = strings.bootingGeneral("account creation strings"),
      interfaceType = ProfileAccountCreationStringResourcesType::class.java,
      serviceConstructor = { MainProfileAccountCreationStringResources(context.resources) }
    )

    addService(
      message = strings.bootingGeneral("account deletion strings"),
      interfaceType = ProfileAccountDeletionStringResourcesType::class.java,
      serviceConstructor = { MainProfileAccountDeletionStringResources(context.resources) }
    )

    addService(
      message = strings.bootingGeneral("book revocation strings"),
      interfaceType = BookRevokeStringResourcesType::class.java,
      serviceConstructor = { MainCatalogBookRevokeStrings(context.resources) }
    )

    addServiceFromServiceLoaderOptionally(
      message = strings.bootingGeneral("Crashlytics"),
      interfaceType = CrashlyticsServiceType::class.java
    )

    AccountProviderRegistryDebugging.load(context.applicationContext)

    val lsHTTP =
      addService(
        message = strings.bootingGeneral("LSHTTP"),
        interfaceType = LSHTTPClientType::class.java,
        serviceConstructor = { MainHTTP.create(context) }
      )

    publishEvent(strings.bootingGeneral("Directories"))
    val directories = initializeDirectories(context)

    val adobeConfiguration = findAdobeConfiguration()
    val adobeDRM =
      addServiceOptionally(
        message = strings.bootingGeneral("Adobe DRM"),
        interfaceType = AdobeAdeptExecutorType::class.java,
        serviceConstructor = {
          createAdobeExecutor(
            context = context,
            adobeConfiguration = adobeConfiguration
          )
        }
      )

    val boundlessDRM =
      addServiceOptionally(
        message = strings.bootingGeneral("Boundless DRM"),
        interfaceType = BoundlessServiceType::class.java,
        serviceConstructor = {
          MainBoundless.createBoundless(directories.directoryStorageBoundless)
        }
      )

    val screenSize =
      addService(
        message = strings.bootingGeneral("screen size"),
        interfaceType = ScreenSizeInformationType::class.java,
        serviceConstructor = { ScreenSizeInformation(context.resources) }
      )

    val bookRegistry =
      addService(
        message = strings.bootingGeneral("book registry"),
        interfaceType = BookRegistryType::class.java,
        serviceConstructor = { BookRegistry.create() }
      )
    addService(
      message = strings.bootingGeneral("catalog book registry"),
      interfaceType = CatalogBookRegistryEvents::class.java,
      serviceConstructor = { CatalogBookRegistryEvents.create(bookRegistry) }
    )
    addService(
      message = strings.bootingGeneral("book registry"),
      interfaceType = BookRegistryReadableType::class.java,
      serviceConstructor = { bookRegistry }
    )

    addService(
      message = strings.bootingGeneral("book preview registry"),
      interfaceType = BookPreviewRegistryType::class.java,
      serviceConstructor = { BookPreviewRegistry(directories.directoryStorageDownloads) }
    )

    addService(
      message = strings.bootingGeneral("accessibility service"),
      interfaceType = AccessibilityServiceType::class.java,
      serviceConstructor = { AccessibilityService.create(context, bookRegistry) }
    )

    val tenPrint =
      addService(
        message = strings.bootingGeneral("10Print"),
        interfaceType = TenPrintGeneratorType::class.java,
        serviceConstructor = { TenPrintGenerator.newGenerator() }
      )

    val coverGenerator =
      addService(
        message = strings.bootingGeneral("cover generator"),
        interfaceType = BookCoverGeneratorType::class.java,
        serviceConstructor = { BookCoverGenerator(tenPrint) }
      )

    addService(
      message = strings.bootingGeneral("local image loader"),
      interfaceType = ImageLoaderType::class.java,
      serviceConstructor = { createLocalImageLoader(context) }
    )

    addService(
      message = strings.bootingGeneral("build configuration service"),
      interfaceType = BuildConfigurationServiceType::class.java,
      serviceConstructor = { findBuildConfiguration() }
    )

    val contentResolver =
      addService(
        message = strings.bootingGeneral("content resolver"),
        interfaceType = ContentResolverType::class.java,
        serviceConstructor = { ContentResolverSane(context.contentResolver) }
      )

    addService(
      message = strings.bootingGeneral("borrow subtask directory"),
      interfaceType = BorrowSubtaskDirectoryType::class.java,
      serviceConstructor = { BorrowSubtasks.directory() }
    )

    val documentConfiguration =
      addServiceOptionally(
        message = strings.bootingGeneral("document configuration service"),
        interfaceType = DocumentConfigurationServiceType::class.java,
        serviceConstructor = {
          optionalFromServiceLoader(DocumentConfigurationServiceType::class.java)
        }
      )

    addService(
      message = strings.bootingGeneral("document store"),
      interfaceType = DocumentStoreType::class.java,
      serviceConstructor = {
        createDocumentStore(
          assets = assets,
          http = lsHTTP,
          directory = directories.directoryStorageDocuments,
          configuration = documentConfiguration
        )
      }
    )

    val accountProviderRegistry =
      addService(
        message = strings.bootingGeneral("account providers"),
        interfaceType = AccountProviderRegistryType::class.java,
        serviceConstructor = { createAccountProviderRegistry(context, lsHTTP) }
      )

    val accountBundledCredentials =
      addService(
        message = strings.bootingGeneral("bundled credentials"),
        interfaceType = AccountBundledCredentialsType::class.java,
        serviceConstructor = { createAccountBundledCredentials(context) }
      )

    val accountCredentials =
      addService(
        message = strings.bootingGeneral("credentials store"),
        interfaceType = AccountAuthenticationCredentialsStoreType::class.java,
        serviceConstructor = { createAccountAuthenticationCredentialsStore(directories) }
      )

    val analytics =
      addService(
        message = strings.bootingGeneral("analytics"),
        interfaceType = AnalyticsType::class.java,
        serviceConstructor = {
          Analytics.create(
            AnalyticsConfiguration(
              context = context,
              http = lsHTTP
            )
          )
        }
      )

    val accountEvents =
      PublishSubject.create<AccountEvent>()

    val feedbooksSecretService =
      addServiceOptionally(
        message = strings.bootingGeneral("Feedbook secret service"),
        interfaceType = AudioBookFeedbooksSecretServiceType::class.java,
        serviceConstructor = { MainFeedbooksSecretService.createConditionally(context) }
      )

    val overdriveSecretService =
      addServiceOptionally(
        message = strings.bootingGeneral("Overdrive secret service"),
        interfaceType = AudioBookOverdriveSecretServiceType::class.java,
        serviceConstructor = { MainOverdriveSecretService.createConditionally(context) }
      )

    val lcpService =
      addServiceOptionally(
        message = strings.bootingGeneral("LCP service"),
        interfaceType = LcpService::class.java,
        serviceConstructor = { MainLCPService.createConditionally(context) }
      )

    val bookFormatService =
      addService(
        message = strings.bootingGeneral("book format support"),
        interfaceType = BookFormatSupportType::class.java,
        serviceConstructor = {
          MainBookFormatSupport.createBookFormatSupport(
            adobeDRM = adobeDRM,
            boundless = boundlessDRM,
            feedbooksSecretService = feedbooksSecretService,
            lcpService = lcpService,
            overdriveSecretService = overdriveSecretService
          )
        }
      )

    addService(
      message = strings.bootingGeneral("profiles database"),
      interfaceType = ProfilesDatabaseType::class.java,
      serviceConstructor = {
        createProfileDatabase(
          context,
          analytics,
          accountEvents,
          accountProviderRegistry,
          accountBundledCredentials,
          accountCredentials,
          bookFormatService,
          directories.directoryStorageProfiles
        )
      }
    )

    val bundledContent =
      addService(
        message = strings.bootingGeneral("bundled content"),
        interfaceType = BundledContentResolverType::class.java,
        serviceConstructor = { MainBundledContentResolver.create(context.assets) }
      )

    val opdsFeedParser =
      addService(
        message = strings.bootingGeneral("feed parser"),
        interfaceType = OPDSFeedParserType::class.java,
        serviceConstructor = {
          createFeedParser()
        }
      )

    val feedLoader =
      addService(
        message = strings.bootingGeneral("feed loader"),
        interfaceType = FeedLoaderType::class.java,
        serviceConstructor = {
          createFeedLoader(
            http = lsHTTP,
            opdsFeedParser = opdsFeedParser,
            bookFormatSupport = bookFormatService,
            bundledContent = bundledContent,
            contentResolver = contentResolver
          )
        }
      )

    addService(
      message = strings.bootingGeneral("patron user profile parsers"),
      interfaceType = PatronUserProfileParsersType::class.java,
      serviceConstructor = { PatronUserProfileParsers() }
    )

    addService(
      message = strings.bootingGeneral("authentication document parsers"),
      interfaceType = AuthenticationDocumentParsersType::class.java,
      serviceConstructor = { AuthenticationDocumentParsers() }
    )

    val notificationTokenHTTPCalls =
      addService(
        message = strings.bootingGeneral("Notification token http calls"),
        interfaceType = NotificationTokenHTTPCallsType::class.java,
        serviceConstructor = {
          NotificationTokenHTTPCalls(
            http = lsHTTP,
            executor = NamedThreadPools.namedThreadPool(1, "http-notifications", 19)
          )
        }
      )

    val profileEvents = PublishSubject.create<ProfileEvent>()

    addService(
      message = strings.bootingGeneral("audio book manifest strategies"),
      interfaceType = AudioBookManifestStrategiesType::class.java,
      serviceConstructor = { return@addService AudioBookManifests }
    )

    var profilesControllerTypeService: ProfilesControllerType

    val bookController = this.run {
      publishEvent(strings.bootingGeneral("books controller"))
      val execBooks =
        NamedThreadPools.namedThreadPool(1, "books", 19)
      val controller =
        Controller.createFromServiceDirectory(
          application = context,
          services = services.build(),
          executorService = execBooks,
          accountEvents = accountEvents,
          profileEvents = profileEvents,
          cacheDirectory = context.cacheDir
        )
      profilesControllerTypeService = addService(
        message = strings.bootingGeneral("profiles controller"),
        interfaceType = ProfilesControllerType::class.java,
        serviceConstructor = { controller }
      )

      addService(
        message = strings.bootingGeneral("notifications service"),
        interfaceType = NotificationsServiceType::class.java,
        serviceConstructor = {
          NotificationsService(
            context = context,
            httpCalls = notificationTokenHTTPCalls,
            notificationResources = MainNotificationResources(context),
            profilesController = profilesControllerTypeService
          )
        }
      )

      addService(
        message = strings.bootingGeneral("books controller"),
        interfaceType = BooksControllerType::class.java,
        serviceConstructor = { controller }
      )
      addService(
        message = strings.bootingGeneral("books preview controller"),
        interfaceType = BooksPreviewControllerType::class.java,
        serviceConstructor = { controller }
      )
      controller
    }

    addService(
      message = strings.bootingGeneral("account events"),
      interfaceType = AccountEvents::class.java,
      serviceConstructor = { AccountEvents.create(profilesControllerTypeService) }
    )
    addService(
      message = strings.bootingGeneral("profile events"),
      interfaceType = SettingsProfileEvents::class.java,
      serviceConstructor = { SettingsProfileEvents.create(profilesControllerTypeService) }
    )

    addService(
      message = strings.bootingGeneral("audiobook time tracker registry"),
      interfaceType = TimeTrackingServiceType::class.java,
      serviceConstructor = {
        TimeTrackingService.create(
          profiles = profilesControllerTypeService,
          httpCalls = TimeTrackingHTTPCalls(lsHTTP),
          clock = { OffsetDateTime.now(UTC) },
          timeSegments = PlayerModel.timeTracker.timeSegments,
          debugDirectory = directories.directoryStorageTimeTrackingDebug.toPath(),
          collectorDirectory = directories.directoryStorageTimeTrackingCollector.toPath(),
          senderDirectory = directories.directoryStorageTimeTrackingSender.toPath()
        )
      }
    )

    publishEvent(strings.bootingGeneral("bookmark service"))
    val bookmarksService =
      createBookmarksService(lsHTTP, bookController)

    addService(
      message = strings.bootingGeneral("bookmarks service"),
      interfaceType = BookmarkServiceType::class.java,
      serviceConstructor = { bookmarksService }
    )

    val badgeLookup =
      addService(
        message = strings.bootingGeneral("book cover badge lookup"),
        interfaceType = BookCoverBadgeLookupType::class.java,
        serviceConstructor = {
          createBookCoverBadgeLookup(
            context = context,
            screenSize = screenSize
          )
        }
      )

    addService(
      message = strings.bootingGeneral("book cover provider"),
      interfaceType = BookCoverProviderType::class.java,
      serviceConstructor = {
        createCoverProvider(
          context = context,
          bookRegistry = bookRegistry,
          bundledContentResolver = bundledContent,
          coverGenerator = coverGenerator,
          badgeLookup = badgeLookup
        )
      }
    )

    addService(
      message = strings.bootingGeneral("book cover provider"),
      interfaceType = CatalogOPDSClients::class.java,
      serviceConstructor = {
        createCatalogOPDSClients(
          profiles = profilesControllerTypeService,
          feedLoader = feedLoader
        )
      }
    )

    val subscription =
      profilesControllerTypeService.profileEvents()
        .ofType(ProfileUpdated::class.java)
        .subscribe {
          onProfileUpdated(profilesControllerTypeService)
        }

    showThreads()

    publishApplicationStartupEvent(context, analytics)
    val finalServices = services.build()
    Services.initialize(finalServices)
    logger.debug("boot completed")
    onProgress.invoke(BootEvent.BootCompleted(strings.bootCompleted))
    return finalServices
  }

  private fun onProfileUpdated(
    profiles: ProfilesControllerType
  ) {
    this.updateReportsLibrary(profiles)
  }

  private fun updateReportsLibrary(profiles: ProfilesControllerType) {
    try {
      val profile = profiles.profileCurrent()
      val account = profile.account(profile.preferences().mostRecentAccount)
      Reports.reportLibrary = account.provider.displayName
    } catch (e: Throwable) {
      this.logger.debug("Failed to handle report update: ", e)
    }
  }

  private fun createCatalogOPDSClients(
    profiles: ProfilesControllerType,
    feedLoader: FeedLoaderType
  ): CatalogOPDSClients {
    val mainClient =
      OPDSClient.create(
        OPDSClientParameters(
          name = "Main",
          runOnUI = UIThread::runOnUIThread,
          checkOnUI = UIThread::checkIsUIThread,
          feedLoader = feedLoader
        )
      )

    val booksClient =
      OPDSClient.create(
        OPDSClientParameters(
          name = "Books",
          runOnUI = UIThread::runOnUIThread,
          checkOnUI = UIThread::checkIsUIThread,
          feedLoader = feedLoader
        )
      )

    val holdsClient =
      OPDSClient.create(
        OPDSClientParameters(
          name = "Holds",
          runOnUI = UIThread::runOnUIThread,
          checkOnUI = UIThread::checkIsUIThread,
          feedLoader = feedLoader
        )
      )

    return CatalogOPDSClients(
      profiles = profiles,
      mainClient = mainClient,
      booksClient = booksClient,
      holdsClient = holdsClient
    )
  }

  private fun createAdobeExecutor(
    context: Application,
    adobeConfiguration: AdobeConfigurationServiceType
  ): AdobeAdeptExecutorType? {
    return if (AdobeDRMServices.isIntendedToBePresent(context)) {
      AdobeDRMServices.newAdobeDRMOrNull(context, adobeConfiguration)
    } else {
      null
    }
  }

  private fun createDocumentStore(
    assets: AssetManager,
    http: LSHTTPClientType,
    configuration: DocumentConfigurationServiceType?,
    directory: File
  ): DocumentStoreType {
    return if (configuration != null) {
      val exec =
        NamedThreadPools.namedThreadPool(1, "documents", 19)

      val store =
        DocumentStores.create(
          assetManager = assets,
          http = http,
          baseDirectory = directory,
          configuration = configuration
        )

      store.update(exec).addListener(Runnable { exec.shutdown() }, exec)
      store
    } else {
      DocumentStores.createEmpty()
    }
  }

  private fun showThreads() {
    val threadSet =
      Thread.getAllStackTraces()
        .keys
        .sortedBy { thread -> thread.name }

    for (thread in threadSet) {
      logger.debug("{}", String.format("[%d] %s", thread.id, thread.name))
    }
  }
}
