package org.librarysimplified.viewer.audiobook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.joda.time.DateTime
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadExpired
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.downloads.DownloadProvider
import org.librarysimplified.audiobook.feedbooks.FeedbooksPlayerExtension
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.open_access.BearerTokenExtension
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.views.PlayerFragment
import org.librarysimplified.audiobook.views.PlayerFragmentListenerType
import org.librarysimplified.audiobook.views.PlayerFragmentParameters
import org.librarysimplified.audiobook.views.PlayerPlaybackRateFragment
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.toc.PlayerTOCFragment
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.audio.AudioBookFeedbooksSecretServiceType
import org.nypl.simplified.books.audio.AudioBookManifestData
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.books.time.tracking.TimeTrackingServiceType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.futures.FluentFutureExtensions.onAnyError
import org.nypl.simplified.networkconnectivity.api.NetworkConnectivityType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.threads.NamedThreadPools
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import rx.Subscription
import java.io.IOException
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The main activity for playing audio books.
 */

class AudioBookPlayerActivity :
  AppCompatActivity(),
  AudioBookLoadingFragmentListenerType,
  PlayerFragmentListenerType {

  private val log: Logger = LoggerFactory.getLogger(AudioBookPlayerActivity::class.java)

  private val contentProtectionProviders =
    ServiceLoader.load(ContentProtectionProvider::class.java).toList()

  companion object {

    private const val PARAMETER_ID =
      "org.nypl.simplified.viewer.audiobook.AudioBookPlayerActivity.parameters"

    /**
     * Start a new player for the given book.
     *
     * @param from The parent activity
     * @param parameters The player parameters
     */

    fun startActivity(
      from: Activity,
      parameters: AudioBookPlayerParameters?
    ) {
      val b = Bundle()
      b.putSerializable(PARAMETER_ID, parameters)
      val i = Intent(from, AudioBookPlayerActivity::class.java)
      i.putExtras(b)
      from.startActivity(i)
    }
  }

  private lateinit var book: PlayerAudioBookType
  private lateinit var bookAuthor: String
  private lateinit var bookSubscription: Subscription
  private lateinit var bookTitle: String
  private lateinit var downloadExecutor: ListeningExecutorService
  private lateinit var downloadProvider: PlayerDownloadProviderType
  private lateinit var formatHandle: BookDatabaseEntryFormatHandleAudioBook
  private lateinit var loadingFragment: AudioBookLoadingFragment
  private lateinit var parameters: AudioBookPlayerParameters
  private lateinit var player: PlayerType
  private lateinit var playerFragment: PlayerFragment
  private lateinit var playerScheduledExecutor: ScheduledExecutorService
  private lateinit var playerSubscription: Subscription
  private lateinit var sleepTimer: PlayerSleepTimerType
  private var playerInitialized: Boolean = false
  private val reloadingManifest = AtomicBoolean(false)

  private val currentBookmarks =
    Collections.synchronizedList(arrayListOf<Bookmark.AudiobookBookmark>())

  private val services =
    Services.serviceDirectory()

  private val bookmarkService =
    this.services.requireService(BookmarkServiceType::class.java)
  private val profilesController =
    this.services.requireService(ProfilesControllerType::class.java)
  private val timeTrackingService =
    this.services.requireService(TimeTrackingServiceType::class.java)
  private val profiles =
    this.services.requireService(ProfilesControllerType::class.java)
  private val uiThread =
    this.services.requireService(UIThreadServiceType::class.java)
  private val books =
    this.services.requireService(BooksControllerType::class.java)
  private val covers =
    this.services.requireService(BookCoverProviderType::class.java)
  private val networkConnectivity =
    this.services.requireService(NetworkConnectivityType::class.java)
  private val strategies =
    this.services.requireService(AudioBookManifestStrategiesType::class.java)

  @Volatile
  private var destroying: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    this.log.debug("onCreate")
    super.onCreate(null)

    val i = this.intent!!
    val a = i.extras!!

    this.parameters = a.getSerializable(PARAMETER_ID) as? AudioBookPlayerParameters ?: kotlin.run {
      val title = getString(R.string.audio_book_player_error_book_open)
      this.showErrorWithRunnable(
        context = this,
        title = title,
        failure = IllegalStateException(title),
        execute = this::finish
      )
      return
    }

    this.log.debug("manifest file: {}", this.parameters.manifestFile)
    this.log.debug("manifest uri:  {}", this.parameters.manifestURI)
    this.log.debug("book id:       {}", this.parameters.bookID)
    this.log.debug("entry id:      {}", this.parameters.opdsEntry.id)

    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, this.parameters.accountID.uuid.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, this.parameters.accountProviderID.toString())
    MDC.put(MDCKeys.BOOK_INTERNAL_ID, this.parameters.bookID.value())
    MDC.put(MDCKeys.BOOK_TITLE, this.parameters.opdsEntry.title)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.parameters.opdsEntry.publisher)
    MDC.remove(MDCKeys.BOOK_DRM)
    MDC.remove(MDCKeys.BOOK_FORMAT)

    this.setContentView(R.layout.audio_book_player_base)
    this.playerScheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    this.supportActionBar?.setDisplayHomeAsUpEnabled(false)

    this.bookTitle = this.parameters.opdsEntry.title
    this.bookAuthor = this.findBookAuthor(this.parameters.opdsEntry)

    /*
     * Open the database format handle.
     */

    val formatHandleOpt =
      this.profiles.profileAccountForBook(this.parameters.bookID)
        .bookDatabase
        .entry(this.parameters.bookID)
        .findFormatHandle(BookDatabaseEntryFormatHandleAudioBook::class.java)

    if (formatHandleOpt == null) {
      val title = getString(R.string.audio_book_player_error_book_open)
      this.showErrorWithRunnable(
        context = this,
        title = title,
        failure = IllegalStateException(title),
        execute = this::finish
      )
      return
    }

    this.formatHandle = formatHandleOpt

    MDC.put(MDCKeys.BOOK_DRM, formatHandle.format.drmInformation.kind.name)
    MDC.put(MDCKeys.BOOK_FORMAT, formatHandle.format.contentType.toString())

    /*
     * Create a new downloader that is solely used to fetch audio book manifests.
     */

    this.downloadExecutor =
      MoreExecutors.listeningDecorator(
        NamedThreadPools.namedThreadPool(1, "audiobook-player", 19)
      )
    this.downloadProvider =
      DownloadProvider.create(this.downloadExecutor)

    /*
     * Create a sleep timer.
     */

    this.sleepTimer = PlayerSleepTimer.create()

    /*
     * Show a loading fragment.
     */

    this.loadingFragment =
      AudioBookLoadingFragment.newInstance(AudioBookLoadingFragmentParameters())

    this.supportFragmentManager.beginTransaction()
      .replace(R.id.audio_book_player_fragment_holder, this.loadingFragment, "LOADING")
      .commit()

    /*
     * Restore the activity title when the back stack is empty.
     */

    this.supportFragmentManager.addOnBackStackChangedListener {
      if (this.supportFragmentManager.backStackEntryCount == 0) {
        this.restoreActionBarTitle()
      }
    }

    this.timeTrackingService.startTimeTracking(
      accountID = this.parameters.accountID,
      bookId = this.parameters.opdsEntry.id,
      libraryId = this.parameters.accountProviderID.toString(),
      timeTrackingUri = this.parameters.opdsEntry.timeTrackingUri.getOrNull()
    )
  }

  private val appCompatDelegate: TxContextWrappingDelegate by lazy {
    TxContextWrappingDelegate(super.getDelegate())
  }

  private fun findBookAuthor(entry: OPDSAcquisitionFeedEntry): String {
    if (entry.authors.isEmpty()) {
      return ""
    }
    return entry.authors.first()
  }

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onDestroy() {
    this.log.debug("onDestroy")
    timeTrackingService.stopTracking()
    super.onDestroy()

    /*
     * We set a flag to indicate that the activity is currently being destroyed because
     * there may be scheduled tasks that try to execute after the activity has stopped. This
     * flag allows them to gracefully avoid running.
     */

    this.destroying = true

    /*
     * Cancel downloads, shut down the player, and close the book.
     */

    if (this.playerInitialized) {
      this.cancelAllDownloads()

      try {
        this.player.close()
      } catch (e: Exception) {
        this.log.error("error closing player: ", e)
      }

      this.bookSubscription.unsubscribe()
      this.playerSubscription.unsubscribe()

      try {
        this.book.close()
      } catch (e: Exception) {
        this.log.error("error closing book: ", e)
      }
    }

    this.downloadExecutor.shutdown()
    this.playerScheduledExecutor.shutdown()
  }

  private fun savePlayerPosition(event: PlayerEventCreateBookmark) {
    try {
      val bookmark = Bookmark.AudiobookBookmark.create(
        opdsId = this.parameters.opdsEntry.id,
        location = PlayerPosition(
          title = event.spineElement.position.title,
          part = event.spineElement.position.part,
          chapter = event.spineElement.position.chapter,
          startOffset = event.spineElement.position.startOffset,
          currentOffset = event.offsetMilliseconds
        ),
        duration = event.spineElement.duration?.millis ?: 0L,
        kind = BookmarkKind.BookmarkLastReadLocation,
        time = DateTime.now(),
        deviceID = AudioBookDevices.deviceId(this.profilesController, this.parameters.bookID),
        // in this case, we can send the uri as null because this is a "last read" bookmark and
        // also because the server will later set an uri, so the next we fetch it, it will have a
        // uri
        uri = null
      )

      if (event.isLocalBookmark) {
        this.bookmarkService.bookmarkCreateLocal(
          accountID = this.parameters.accountID,
          bookmark = bookmark
        )
      } else {
        this.bookmarkService.bookmarkCreateRemote(
          accountID = this.parameters.accountID,
          bookmark = bookmark
        )
      }
    } catch (e: Exception) {
      this.log.error("could not save player position: ", e)
    }
  }

  override fun onLoadingFragmentWantsIOExecutor(): ListeningExecutorService {
    return this.downloadExecutor
  }

  override fun onLoadingFragmentIsNetworkConnectivityAvailable(): Boolean {
    return this.networkConnectivity.isNetworkAvailable
  }

  override fun onLoadingFragmentWantsAudioBookParameters(): AudioBookPlayerParameters {
    return this.parameters
  }

  override fun onLoadingFragmentLoadingFailed(exception: Exception) {
    this.showErrorWithRunnable(
      context = this,
      title = exception.message ?: "",
      failure = exception,
      execute = this::finish
    )
  }

  override fun onLoadingFragmentLoadingFinished(
    manifest: PlayerManifest,
    authorization: LSHTTPAuthorizationType?
  ) {
    this.log.debug("finished loading")

    val contentProtections = BookContentProtections.create(
      context = this,
      contentProtectionProviders = this.contentProtectionProviders,
      drmInfo = this.parameters.drmInfo,
      isManualPassphraseEnabled =
      profilesController.profileCurrent().preferences().isManualLCPPassphraseEnabled,
      onLCPDialogDismissed = {
        finish()
      }
    )

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine = PlayerAudioEngines.findBestFor(
      PlayerAudioEngineRequest(
        file = this.parameters.file,
        manifest = manifest,
        filter = { true },
        downloadProvider = DownloadProvider.create(this.downloadExecutor),
        userAgent = PlayerUserAgent(this.parameters.userAgent),
        contentProtections = contentProtections,
        manualPassphrase =
        profilesController.profileCurrent().preferences().isManualLCPPassphraseEnabled
      )
    )

    if (engine == null) {
      val title = getString(R.string.audio_book_player_error_engine_open)
      this.showErrorWithRunnable(
        context = this,
        title = title,
        failure = IllegalStateException(title),
        execute = this::finish
      )
      return
    }

    this.log.debug(
      "selected audio engine: {} {}",
      engine.engineProvider.name(),
      engine.engineProvider.version()
    )

    /*
     * Load extensions.
     */

    val extensions =
      this.loadAndConfigureExtensions(authorization)

    /*
     * Create the audio book.
     */

    val bookResult =
      engine.bookProvider.create(
        context = this,
        extensions = extensions
      )

    if (bookResult is PlayerResult.Failure) {
      val title = getString(R.string.audio_book_player_error_book_open)
      this.showErrorWithRunnable(
        context = this,
        title = title,
        failure = bookResult.failure,
        execute = this::finish
      )
      return
    }

    this.book = (bookResult as PlayerResult.Success).result
    this.player = this.book.createPlayer()

    this.bookSubscription =
      this.book.spineElementDownloadStatus.ofType(PlayerSpineElementDownloadExpired::class.java)
        .subscribe(this::onDownloadExpired)
    this.playerSubscription =
      this.player.events.subscribe(this::onPlayerEvent)

    this.playerInitialized = true

    this.restoreSavedPlayerPosition()
    this.startAllPartsDownloading()

    /*
     * Create and load the main player fragment into the holder view declared in the activity.
     */

    this.uiThread.runOnUIThread {
      // Sanity check; Verify the state of the lifecycle before continuing as it's possible the
      // activity could be finishing.
      if (!this.isFinishing && !this.supportFragmentManager.isDestroyed) {
        this.playerFragment = PlayerFragment.newInstance(
          PlayerFragmentParameters(
            currentRate = getBookCurrentPlaybackRate(),
            currentSleepTimerDuration = getBookSleepTimerRemainingDuration()
          )
        )

        this.supportFragmentManager
          .beginTransaction()
          .replace(R.id.audio_book_player_fragment_holder, this.playerFragment, "PLAYER")
          .commitAllowingStateLoss()
      }
    }
  }

  private fun downloadAndSaveManifest(
    credentials: AccountAuthenticationCredentials?
  ): AudioBookManifestData {
    this.log.debug("downloading and saving manifest")
    val strategy =
      this.parameters.toManifestStrategy(
        strategies = this.strategies,
        isNetworkAvailable = { this.networkConnectivity.isNetworkAvailable },
        credentials = credentials,
        cacheDirectory = this.cacheDir
      )
    return when (val strategyResult = strategy.execute()) {
      is TaskResult.Success -> {
        AudioBookHelpers.saveManifest(
          profiles = this.profiles,
          bookId = this.parameters.bookID,
          manifestURI = this.parameters.manifestURI,
          manifest = strategyResult.result.fulfilled
        )
        strategyResult.result
      }

      is TaskResult.Failure ->
        throw IOException(strategyResult.message)
    }
  }

  private fun onDownloadExpired(event: PlayerSpineElementDownloadExpired) {
    this.log.debug("onDownloadExpired: ", event.exception)

    if (this.reloadingManifest.compareAndSet(false, true)) {
      this.log.debug("attempting to download fresh manifest due to expired links")
      this.downloadExecutor.execute {
        try {
          val manifestData = this.downloadAndSaveManifest(
            this.profiles.profileAccountForBook(this.parameters.bookID)
              .loginState
              .credentials
          )

          this.book.replaceManifest(manifestData.manifest)
        } catch (e: Exception) {
          this.log.error("onDownloadExpired: failed to download/replace manifest: ", e)
        } finally {
          this.reloadingManifest.set(false)
        }
      }
    }
  }

  private fun getBookCurrentPlaybackRate(): PlayerPlaybackRate? {
    val playbackRates = this.profilesController.profileCurrent().preferences().playbackRates
    val bookID = this.parameters.bookID.value()
    return playbackRates[bookID]
  }

  private fun getBookSleepTimerRemainingDuration(): Long? {
    val sleepTimers = this.profilesController.profileCurrent().preferences().sleepTimers
    val bookID = this.parameters.bookID.value()
    return if (sleepTimers.containsKey(bookID)) {
      sleepTimers[bookID]
    } else {
      0L
    }
  }

  private fun loadAndConfigureExtensions(
    authorization: LSHTTPAuthorizationType?
  ): List<PlayerExtensionType> {
    val extensions =
      ServiceLoader.load(PlayerExtensionType::class.java)
        .toList()

    val services = Services.serviceDirectory()
    this.loadAndConfigureBearerToken(extensions, authorization)
    this.loadAndConfigureFeedbooks(services, extensions)
    return extensions
  }

  private fun loadAndConfigureFeedbooks(
    services: ServiceDirectoryType,
    extensions: List<PlayerExtensionType>
  ) {
    val feedbooksConfigService =
      services.optionalService(AudioBookFeedbooksSecretServiceType::class.java)

    if (feedbooksConfigService != null) {
      this.log.debug("feedbooks configuration service is available; configuring extension")
      val extension =
        extensions.filterIsInstance<FeedbooksPlayerExtension>()
          .firstOrNull()
      if (extension != null) {
        this.log.debug("feedbooks extension is available")
        extension.configuration = feedbooksConfigService.configuration
      } else {
        this.log.debug("feedbooks extension is not available")
      }
    }
  }

  private fun loadAndConfigureBearerToken(
    extensions: List<PlayerExtensionType>,
    authorization: LSHTTPAuthorizationType?
  ) {
    this.log.debug(
      "configuring bearer token extension with authorization: {}",
      authorization?.toHeaderValue()
    )
    val extension =
      extensions.filterIsInstance<BearerTokenExtension>()
        .firstOrNull()
    if (extension != null) {
      this.log.debug("bearer token extension is available")
      extension.authorization = authorization
    } else {
      this.log.debug("bearer token extension is not available")
    }
  }

  private fun restoreSavedPlayerPosition() {
    val bookmarks =
      AudioBookHelpers.loadBookmarks(
        bookmarkService = this.bookmarkService,
        accountID = this.parameters.accountID,
        bookID = this.parameters.bookID
      )

    try {
      val audiobookBookmarks = bookmarks
        .filterIsInstance<Bookmark.AudiobookBookmark>()

      val bookMarkLastReadPosition = audiobookBookmarks.find { bookmark ->
        bookmark.kind == BookmarkKind.BookmarkLastReadLocation
      }

      currentBookmarks.addAll(
        audiobookBookmarks.filter { bookmark ->
          bookmark.kind == BookmarkKind.BookmarkExplicit && bookmark.uri != null
        }
      )

      val position =
        bookMarkLastReadPosition?.location ?: this.formatHandle.format.lastReadLocation?.location

      this.player.movePlayheadToLocation(
        // Explicitly wind back to the start of the book if there isn't a suitable position saved.
        location = position ?: this.book.spine.first().position,
        playAutomatically = false
      )
    } catch (e: Exception) {
      this.log.error("unable to load saved player position: ", e)
    }
  }

  private fun startAllPartsDownloading() {
    if (this.networkConnectivity.isNetworkAvailable) {
      this.book.wholeBookDownloadTask.fetch()
    }
  }

  private fun cancelAllDownloads() {
    this.book.wholeBookDownloadTask.cancel()
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    timeTrackingService.onPlayerEventReceived(event)

    return when (event) {
      is PlayerEventCreateBookmark -> {
        this.savePlayerPosition(event)
      }

      is PlayerEventPlaybackStarted -> {
        this.savePlayerPosition(
          PlayerEventCreateBookmark(
            event.spineElement,
            event.offsetMilliseconds,
            isLocalBookmark = false
          )
        )
      }

      is PlayerEventPlaybackPaused -> {
        this.savePlayerPosition(
          PlayerEventCreateBookmark(
            event.spineElement,
            event.offsetMilliseconds,
            isLocalBookmark = false
          )
        )
      }

      is PlayerEventPlaybackStopped -> {
        this.savePlayerPosition(
          PlayerEventCreateBookmark(
            event.spineElement,
            event.offsetMilliseconds,
            isLocalBookmark = false
          )
        )
      }

      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackProgressUpdate,
      is PlayerEventPlaybackWaitingForAction -> {
      }

      is PlayerEventChapterCompleted ->
        this.onPlayerChapterCompleted(event)

      is PlayerEventChapterWaiting -> Unit
      is PlayerEventPlaybackRateChanged -> {
        this.onPlaybackRateChanged(event.rate)
      }

      is PlayerEventError ->
        this.onLogPlayerError(event)

      PlayerEvent.PlayerEventManifestUpdated ->
        Unit
    }
  }

  private fun onPlayerChapterCompleted(event: PlayerEventChapterCompleted) {
    if (event.spineElement.next == null) {
      this.log.debug("book has finished")

      /*
       * Wait a few seconds before displaying the dialog asking if the user wants
       * to return the book.
       */

      this.playerScheduledExecutor.schedule(
        {
          if (!this.destroying) {
            this.uiThread.runOnUIThread { this.loanReturnShowDialog() }
          }
        },
        5L, TimeUnit.SECONDS
      )
    }
  }

  private fun onPlaybackRateChanged(playbackRate: PlayerPlaybackRate) {
    val playbackRates =
      HashMap(this.profilesController.profileCurrent().preferences().playbackRates)
    val bookID = this.parameters.bookID.value()
    playbackRates[bookID] = playbackRate

    this.profilesController.profileUpdate { current ->
      current.copy(
        preferences = current.preferences.copy(
          playbackRates = playbackRates
        )
      )
    }
  }

  private fun loanReturnShowDialog() {
    val alert = AlertDialog.Builder(this)
    alert.setTitle(R.string.audio_book_player_return_title)
    alert.setMessage(R.string.audio_book_player_return_question)
    alert.setNegativeButton(R.string.audio_book_player_do_keep) { dialog, _ ->
      dialog.dismiss()
    }
    alert.setPositiveButton(R.string.audio_book_player_do_return) { _, _ ->
      this.loanReturnPerform()
      this.finish()
    }
    alert.show()
  }

  private fun loanReturnPerform() {
    this.log.debug("returning loan")

    /*
     * We don't care if the return fails. The user can retry when they get back to their
     * book list, if necessary.
     */

    try {
      this.books.bookRevoke(this.parameters.accountID, this.parameters.bookID)
    } catch (e: Exception) {
      this.log.error("could not execute revocation: ", e)
    }
  }

  private fun onLogPlayerError(event: PlayerEventError) {
    val builder = StringBuilder(128)
    builder.append("Playback error:")
    builder.append('\n')
    builder.append("  Error Code:    ")
    builder.append(event.errorCode)
    builder.append('\n')
    builder.append("  Spine Element: ")
    builder.append(event.spineElement)
    builder.append('\n')
    builder.append("  Offset:        ")
    builder.append(event.offsetMilliseconds)
    builder.append('\n')
    builder.append("  Book Title:    ")
    builder.append(this.parameters.opdsEntry.title)
    builder.append('\n')
    builder.append("  Book OPDS ID:  ")
    builder.append(this.parameters.opdsEntry.id)
    builder.append('\n')
    builder.append("  Stacktrace:")
    builder.append('\n')
    this.log.error("{}", builder.toString(), event.exception)
  }

  override fun onPlayerPlaybackRateShouldOpen() {
    /*
     * The player fragment wants us to open the playback rate selection dialog.
     */

    this.uiThread.runOnUIThread {
      val fragment =
        PlayerPlaybackRateFragment.newInstance(
          PlayerFragmentParameters(
            currentRate = getBookCurrentPlaybackRate(),
            currentSleepTimerDuration = getBookSleepTimerRemainingDuration()
          )
        )
      fragment.show(this.supportFragmentManager, "PLAYER_RATE")
    }
  }

  override fun onPlayerSleepTimerShouldOpen() {
    /*
     * The player fragment wants us to open the sleep timer.
     */

    this.uiThread.runOnUIThread {
      val fragment = PlayerSleepTimerFragment.newInstance()
      fragment.show(this.supportFragmentManager, "PLAYER_SLEEP_TIMER")
    }
  }

  override fun onPlayerSleepTimerUpdated(remainingDuration: Long?) {
    val sleepTimers =
      HashMap(this.profilesController.profileCurrent().preferences().sleepTimers)
    val bookID = this.parameters.bookID.value()
    sleepTimers[bookID] = remainingDuration

    this.profilesController.profileUpdate { current ->
      current.copy(
        preferences = current.preferences.copy(
          sleepTimers = sleepTimers
        )
      )
    }
  }

  override fun onPlayerTOCShouldOpen() {
    /*
     * The player fragment wants us to open the table of contents. Load and display it, and
     * also set the action bar title.
     */

    this.uiThread.runOnUIThread {
      this.supportActionBar?.setTitle(
        org.librarysimplified.audiobook.views.R.string.audiobook_player_toc_title
      )

      val fragment = PlayerTOCFragment.newInstance()

      this.supportFragmentManager
        .beginTransaction()
        .hide(this.playerFragment)
        .add(R.id.audio_book_player_fragment_holder, fragment, "PLAYER_TOC")
        .addToBackStack(null)
        .commit()
    }
  }

  override fun onPlayerNotificationWantsIntent(): Intent {
    return parentActivityIntent ?: intent
  }

  override fun onPlayerTOCWantsBook(): PlayerAudioBookType {
    return this.book
  }

  override fun onPlayerShouldBeClosed() {
    onBackPressed()
  }

  override fun onPlayerTOCWantsClose() {
    /*
     * The player fragment wants to close the table of contents dialog. Pop it from the back
     * stack and set the action bar title back to the original title.
     */

    this.supportFragmentManager.popBackStack()
    this.restoreActionBarTitle()
  }

  private fun restoreActionBarTitle() {
    this.supportActionBar?.setTitle(R.string.audio_book_player)
  }

  override fun onPlayerWantsAuthor(): String {
    return this.bookAuthor
  }

  override fun onPlayerNotificationWantsSmallIcon(): Int {
    return R.drawable.main_icon
  }

  override fun onPlayerNotificationWantsBookCover(onBookCoverLoaded: (Bitmap) -> Unit) {
    this.covers.loadCoverAsBitmap(
      FeedEntry.FeedEntryOPDS(this.parameters.accountID, this.parameters.opdsEntry),
      onBookCoverLoaded,
      R.drawable.main_icon
    )
  }

  override fun onPlayerWantsCoverImage(view: ImageView) {
    this.covers.loadCoverInto(
      entry = FeedEntry.FeedEntryOPDS(this.parameters.accountID, this.parameters.opdsEntry),
      hasBadge = false,
      imageView = view,
      width = 0,
      height = 0
    )
  }

  override fun onPlayerWantsPlayer(): PlayerType {
    return this.player
  }

  override fun onPlayerWantsSleepTimer(): PlayerSleepTimerType {
    return this.sleepTimer
  }

  override fun onPlayerWantsTitle(): String {
    return this.parameters.opdsEntry.title
  }

  override fun onPlayerWantsScheduledExecutor(): ScheduledExecutorService {
    return this.playerScheduledExecutor
  }

  override fun onPlayerAccessibilityEvent(event: PlayerAccessibilityEvent) {
    // do nothing
  }

  override fun onPlayerTOCWantsBookmarks(): List<PlayerBookmark> {
    return currentBookmarks
      .map { bookmark ->
        AudioBookHelpers.toPlayerBookmark(bookmark)
      }
      .sortedByDescending { bookmark ->
        bookmark.date.millis
      }
  }

  override fun onPlayerShouldDeleteBookmark(
    playerBookmark: PlayerBookmark,
    onDeleteOperationCompleted: (Boolean) -> Unit
  ) {
    val bookmark = AudioBookHelpers.fromPlayerBookmark(
      opdsId = this.parameters.opdsEntry.id,
      deviceID = AudioBookDevices.deviceId(this.profilesController, this.parameters.bookID),
      playerBookmark = playerBookmark
    )

    this.bookmarkService.bookmarkDelete(
      accountID = this.parameters.accountID,
      bookmark = bookmark,
      ignoreRemoteFailures = true
    ).map {
      this.currentBookmarks.remove(bookmark)
      runOnUiThread {
        onDeleteOperationCompleted(true)
      }
    }.onAnyError {
      runOnUiThread {
        onDeleteOperationCompleted(false)
      }
    }
  }

  override fun onPlayerShouldAddBookmark(playerBookmark: PlayerBookmark?) {
    if (playerBookmark == null) {
      showToastMessage(R.string.audio_book_player_bookmark_error_adding)
      return
    }

    try {
      val bookmark = AudioBookHelpers.fromPlayerBookmark(
        opdsId = this.parameters.opdsEntry.id,
        deviceID = AudioBookDevices.deviceId(this.profilesController, this.parameters.bookID),
        playerBookmark = playerBookmark
      )

      if (!currentBookmarks.any { it.location == bookmark.location }) {
        showToastMessage(R.string.audio_book_player_bookmark_adding)

        this.bookmarkService.bookmarkCreate(
          accountID = this.parameters.accountID,
          bookmark = bookmark,
          ignoreRemoteFailures = true
        ).map { savedBookmark ->
          this.currentBookmarks.add(savedBookmark as Bookmark.AudiobookBookmark)
          showToastMessage(R.string.audio_book_player_bookmark_added)
        }.onAnyError {
          /* Otherwise, something in the chain failed. */
          showToastMessage(R.string.audio_book_player_bookmark_error_adding)
        }
      } else {
        showToastMessage(R.string.audio_book_player_bookmark_already_added)
      }
    } catch (e: Exception) {
      showToastMessage(R.string.audio_book_player_bookmark_error_adding)
      this.log.error("could not save bookmark: ", e)
    }
  }

  private fun showToastMessage(@StringRes messageRes: Int) {
    runOnUiThread {
      Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
  }

  private fun showErrorWithRunnable(
    context: Context,
    title: String,
    failure: Exception,
    execute: () -> Unit
  ) {
    this.log.error("error: {}: ", title, failure)

    this.uiThread.runOnUIThread {
      AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(failure.localizedMessage)
        .setOnDismissListener {
          execute.invoke()
        }
        .show()
    }
  }
}
