package org.librarysimplified.viewer.audiobook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate2
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jfunctional.Some
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityChapterSelected
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityErrorOccurred
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityIsBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityIsWaitingForChapter
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilitySleepTimerSettingChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.views.PlayerBaseFragment
import org.librarysimplified.audiobook.views.PlayerBookmarkModel
import org.librarysimplified.audiobook.views.PlayerFragment
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerModelState
import org.librarysimplified.audiobook.views.PlayerPlaybackRateFragment
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.PlayerTOCFragment
import org.librarysimplified.audiobook.views.PlayerViewCommand
import org.librarysimplified.services.api.Services
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.books.time.tracking.TimeTrackingServiceType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.errorpage.ErrorPageFragment
import org.nypl.simplified.ui.errorpage.ErrorPageModel
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.screen.ScreenEdgeToEdgeFix
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.URI

class AudioBookPlayerActivity2 : AppCompatActivity(R.layout.audio_book_player_base) {

  private val logger =
    LoggerFactory.getLogger(AudioBookPlayerActivity2::class.java)

  private lateinit var root: View
  private lateinit var bookmarkService: BookmarkServiceType
  private lateinit var buildConfig: BuildConfigurationServiceType
  private lateinit var coverService: BookCoverProviderType
  private lateinit var profiles: ProfilesControllerType
  private lateinit var timeTrackingService: TimeTrackingServiceType

  private var fragmentNow: Fragment = AudioBookLoadingFragment2()
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  private val appCompatDelegate: TxContextWrappingDelegate2 by lazy {
    TxContextWrappingDelegate2(super.getDelegate())
  }

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
    super.onCreate(savedInstanceState)

    val services =
      Services.serviceDirectory()

    this.buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)
    this.bookmarkService =
      services.requireService(BookmarkServiceType::class.java)
    this.coverService =
      services.requireService(BookCoverProviderType::class.java)
    this.timeTrackingService =
      services.requireService(TimeTrackingServiceType::class.java)
    this.profiles =
      services.requireService(ProfilesControllerType::class.java)

    this.root = this.findViewById(R.id.audio_book_player_fragment_root)
    ScreenEdgeToEdgeFix.edgeToEdge(this.root)
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))

    this.loadCoverImage()

    val parameters = AudioBookViewerModel.parameters ?: return
    this.bookmarkService.bookmarkSyncAndLoad(
      accountID = parameters.accountID,
      book = parameters.bookID
    )
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    return when (val f = this.fragmentNow) {
      is PlayerBaseFragment -> {
        when (f) {
          is PlayerFragment -> {
            this.close()
          }

          is PlayerTOCFragment -> {
            this.switchFragment(PlayerFragment())
          }
        }
      }

      is ErrorPageFragment -> {
        this.switchFragment(PlayerTOCFragment())
      }

      else -> {
        this.close()
      }
    }
  }

  private fun close() {
    try {
      PlayerModel.closeBookOrDismissError()
    } catch (e: Exception) {
      this.logger.debug("Failed to close book: ", e)
    }

    try {
      this.timeTrackingService.onBookClosed()
    } catch (e: Exception) {
      this.logger.debug("Failed to stop time tracking: ", e)
    }

    try {
      this.finish()
    } catch (e: Exception) {
      this.logger.debug("Failed to finish activity: ", e)
    }
  }

  @UiThread
  private fun onPlayerEvent(
    event: PlayerEvent
  ) {
    PlayerUIThread.checkIsUIThread()

    when (event) {
      is PlayerAccessibilityChapterSelected,
      is PlayerAccessibilityErrorOccurred,
      is PlayerAccessibilityIsBuffering,
      is PlayerAccessibilityIsWaitingForChapter,
      is PlayerAccessibilityPlaybackRateChanged,
      is PlayerAccessibilitySleepTimerSettingChanged,
      is PlayerEventChapterCompleted,
      is PlayerEventChapterWaiting,
      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackPreparing,
      is PlayerEventPlaybackProgressUpdate,
      is PlayerEventPlaybackStarted,
      is PlayerEventPlaybackStopped,
      is PlayerEventPlaybackWaitingForAction -> {
        // Nothing to do
      }

      is PlayerEventCreateBookmark -> {
        val parameters =
          AudioBookViewerModel.parameters ?: return

        val playerBookmark =
          PlayerBookmark(
            kind = event.kind,
            readingOrderID = event.readingOrderItem.id,
            offsetMilliseconds = event.readingOrderItemOffsetMilliseconds,
            metadata = event.bookmarkMetadata
          )

        this.logger.debug(
          "Creating bookmark (Kind {}, Position {})",
          playerBookmark.kind,
          playerBookmark.position
        )
        this.bookmarkService.bookmarkCreate(
          accountID = parameters.accountID,
          bookmark = AudioBookBookmarks.fromPlayerBookmark(
            feedEntry = parameters.opdsEntry,
            deviceId = "null",
            source = playerBookmark
          ),
          ignoreRemoteFailures = true,
        )

        /*
         * Tell the bookmark model about the new bookmark.
         */

        when (event.kind) {
          PlayerBookmarkKind.EXPLICIT -> {
            val newBookmarks = arrayListOf<PlayerBookmark>()
            newBookmarks.addAll(PlayerBookmarkModel.bookmarks())
            newBookmarks.removeIf { b -> b.position == playerBookmark.position }
            newBookmarks.add(0, playerBookmark)
            PlayerBookmarkModel.setBookmarks(newBookmarks.toList())
          }

          PlayerBookmarkKind.LAST_READ -> {
            // Nothing to do here.
          }
        }

        /*
         * Show a toast message for explicit bookmarks.
         */

        when (event.kind) {
          PlayerBookmarkKind.EXPLICIT -> {
            Toast.makeText(this, R.string.audio_book_player_bookmark_added, Toast.LENGTH_SHORT)
              .show()
          }

          PlayerBookmarkKind.LAST_READ -> {
            // Nothing to do here.
          }
        }
      }

      is PlayerEventDeleteBookmark -> {
        val parameters =
          AudioBookViewerModel.parameters ?: return

        val playerBookmark =
          event.bookmark
        val appBookmark =
          AudioBookBookmarks.fromPlayerBookmark(
            feedEntry = parameters.opdsEntry,
            deviceId = "null",
            source = playerBookmark
          )
        this.bookmarkService.bookmarkDelete(
          accountID = parameters.accountID,
          bookmark = appBookmark,
          ignoreRemoteFailures = true,
        )

        val newBookmarks = arrayListOf<PlayerBookmark>()
        newBookmarks.addAll(PlayerBookmarkModel.bookmarks())
        newBookmarks.remove(playerBookmark)
        PlayerBookmarkModel.setBookmarks(newBookmarks.toList())
      }

      is PlayerEventError -> {
        // Nothing yet...
      }

      is PlayerEventManifestUpdated -> {
        // Nothing yet...
      }

      is PlayerEventPlaybackRateChanged -> {
        val parameters =
          AudioBookViewerModel.parameters ?: return

        this.profiles.profileUpdate { description ->
          description.copy(
            preferences = description.preferences.copy(
              playbackRates = description.preferences.playbackRates.plus(
                Pair(parameters.bookID.value(), event.rate)
              )
            )
          )
        }
      }
    }
  }

  @UiThread
  private fun onModelStateEvent(
    state: PlayerModelState
  ) {
    PlayerUIThread.checkIsUIThread()

    val bookParameters =
      AudioBookViewerModel.parameters ?: return

    when (state) {
      is PlayerModelState.PlayerBookOpenFailed -> {
        this.onBookOpenFailed(state)
      }

      PlayerModelState.PlayerClosed -> {
        this.timeTrackingService.onBookClosed()
        this.switchFragment(AudioBookLoadingFragment2())
      }

      is PlayerModelState.PlayerManifestDownloadFailed -> {
        this.onManifestDownloadFailed(state)
      }

      is PlayerModelState.PlayerManifestLicenseChecksFailed -> {
        this.onManifestLicenseChecksFailed(state)
      }

      is PlayerModelState.PlayerManifestOK -> {
        AudioBookViewerModel.appliedLastReadBookmarkMigration = false

        val timeTrackingUri = bookParameters.opdsEntry.timeTrackingUri.getOrNull()
        if (timeTrackingUri != null) {
          this.logger.debug("Time tracking info will be sent to {}", timeTrackingUri)
          this.timeTrackingService.onBookOpenedForTracking(
            accountID = bookParameters.accountID,
            bookId = PlayerPalaceID(bookParameters.opdsEntry.id),
            libraryId = bookParameters.accountProviderID.toString(),
            timeTrackingUri = timeTrackingUri
          )
        } else {
          this.logger.debug("Book has no time tracking URI. No time tracking will occur.")
        }

        PlayerModel.openPlayerForManifest(
          context = this.application,
          userAgent = PlayerUserAgent(bookParameters.userAgent),
          manifest = state.manifest,
          fetchAll = true,
          bookSource = state.bookSource,
          bookCredentials = bookParameters.drmInfo.playerCredentials()
        )
      }

      is PlayerModelState.PlayerManifestParseFailed -> {
        this.onManifestParseFailed(state)
      }

      is PlayerModelState.PlayerOpen -> {
        this.handleBookmarks(state, bookParameters)

        this.loadCoverImage()
        this.setPlaybackRateFromPreferencesIfRequired()
        this.switchFragment(PlayerFragment())
      }

      PlayerModelState.PlayerManifestInProgress -> {
        this.switchFragment(AudioBookLoadingFragment2())
      }
    }
  }

  /**
   * As of 2025-07-29 (Audiobooks 17.0.*), the app is no longer responsible for saving, restoring,
   * or syncing last read positions for audiobooks. The player now stores this state internally.
   * However, someone upgrading to 17.0.0 from a previous version would obviously _only_ have the
   * last read position stored in the app's bookmarks (if they have anything at all). Therefore, if
   * the player announces here that it doesn't have a stored last-read position, then we take the
   * last-read position from the app's bookmarks (if one exists).
   */

  private fun handleBookmarks(
    state: PlayerModelState.PlayerOpen,
    bookParameters: AudioBookPlayerParameters
  ) {
    try {
      val bookmarksAll =
        this.bookmarkService.bookmarks.get()
      val bookmarksForAccount =
        bookmarksAll[bookParameters.accountID] ?: mapOf()
      val bookmarks =
        bookmarksForAccount[bookParameters.bookID] ?: BookmarksForBook.empty(bookParameters.bookID)

      this.logger.debug("Assigning {} bookmarks.", bookmarks.bookmarks.size)
      val lastRead = this.assignBookmarks(bookmarks) ?: return

      if (state.positionOnOpen != null) {
        this.logger.debug("Player already has a saved last-read position.")
        return
      }
      if (AudioBookViewerModel.appliedLastReadBookmarkMigration) {
        this.logger.debug("Player has already applied the last-read bookmark migration.")
        return
      }
      this.logger.debug("Restoring last-read position from bookmark ({}).", lastRead.position)
      AudioBookViewerModel.appliedLastReadBookmarkMigration = true
      state.player.player.movePlayheadToLocation(lastRead.position)
    } catch (e: Throwable) {
      MDC.put("Ticket", "PP-2680")
      this.logger.error("Timed out waiting for audiobooks bookmarks!")
      MDC.remove("Ticket")
    }
  }

  private fun setPlaybackRateFromPreferencesIfRequired() {
    val bookParameters =
      AudioBookViewerModel.parameters ?: return
    val preferencesRate =
      this.profiles.profileCurrent()
        .preferences()
        .playbackRates
        .get(bookParameters.bookID.value())

    if (preferencesRate == null) {
      return
    }

    if (preferencesRate != PlayerModel.playbackRate) {
      PlayerModel.setPlaybackRate(preferencesRate)
    }
  }

  private fun compareBookmarks(
    x: PlayerBookmark,
    y: PlayerBookmark
  ): Int {
    val c = x.readingOrderID.text.compareTo(y.readingOrderID.text)
    if (c == 0) {
      return x.offsetMilliseconds.compareTo(y.offsetMilliseconds)
    }
    return c
  }

  private fun assignBookmarks(
    bookmarks: BookmarksForBook
  ): PlayerBookmark? {
    val bookmarksConverted =
      bookmarks.bookmarks.mapNotNull(AudioBookBookmarks::toPlayerBookmark)
        .toSet()
        .sortedWith { x, y -> compareBookmarks(x, y) }
        .toList()
    val bookmarkLastRead =
      bookmarks.lastRead?.let { b -> AudioBookBookmarks.toPlayerBookmark(b) }

    PlayerBookmarkModel.setBookmarks(bookmarksConverted)
    return bookmarkLastRead
  }

  private fun loadCoverImage() {
    val parameters = AudioBookViewerModel.parameters ?: return

    /*
     * Set the cover image (loading it asynchronously).
     */

    val coverURI = parameters.opdsEntry.cover
    if (coverURI is Some<URI>) {
      this.coverService.loadCoverAsBitmap(
        source = coverURI.get(),
        onBitmapLoaded = PlayerModel::setCoverImage,
        defaultResource = R.drawable.empty
      )
    }
  }

  private fun onManifestParseFailed(
    state: PlayerModelState.PlayerManifestParseFailed
  ) {
    this.logger.error("onManifestParseFailed: {}", state)

    val task = TaskRecorder.create()
    task.beginNewStep("Parsing manifest…")

    val extraMessages =
      state.failure.map { error -> "${error.line}:${error.column}: ${error.message}" }

    task.currentStepFailed(
      message = "Parsing failed.",
      errorCode = "error-manifest-parse",
      extraMessages = extraMessages
    )

    val alert = MaterialAlertDialogBuilder(this)
    alert.setTitle(R.string.audio_book_player_error_book_open)
    alert.setMessage(R.string.audio_book_manifest_parse_error)
    alert.setNeutralButton(R.string.audio_book_player_details) { dialog, _ ->
      this.openErrorPage(task.finishFailure<String>())
    }
    alert.setPositiveButton(R.string.audio_book_player_ok) { dialog, _ ->
      dialog.dismiss()
      this.close()
    }
    alert.show()
  }

  private fun openErrorPage(result: TaskResult.Failure<*>) {
    ErrorPageModel.parameters =
      ErrorPageParameters(
        emailAddress = this.buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[palace-audiobook-error-report]",
        attributes = sortedMapOf(),
        taskSteps = result.steps
      )

    this.switchFragment(ErrorPageFragment())
  }

  private fun onBookOpenFailed(
    state: PlayerModelState.PlayerBookOpenFailed
  ) {
    this.logger.error("onBookOpenFailed: {}", state)

    val task = TaskRecorder.create()
    task.beginNewStep("Opening book…")
    task.currentStepFailed(
      message = state.message,
      errorCode = "error-book-open",
      exception = state.exception,
      extraMessages = listOf()
    )

    val alert = MaterialAlertDialogBuilder(this)
    alert.setTitle(R.string.audio_book_player_error_book_open)
    alert.setMessage(state.message)
    alert.setNeutralButton(R.string.audio_book_player_details) { dialog, _ ->
      this.openErrorPage(task.finishFailure<String>())
    }
    alert.setPositiveButton(R.string.audio_book_player_ok) { dialog, _ ->
      dialog.dismiss()
      this.close()
    }
    alert.show()
  }

  private fun onManifestDownloadFailed(
    state: PlayerModelState.PlayerManifestDownloadFailed
  ) {
    this.logger.error("onManifestDownloadFailed: {}", state)

    val task = TaskRecorder.create()
    task.beginNewStep("Downloading manifest…")
    val serverData = state.failure.serverData
    if (serverData != null) {
      task.addAttributes(serverData.problemReport?.toMap() ?: mapOf())
      task.addAttribute("URI", serverData.uri.toString())
      task.addAttribute("Code", serverData.code.toString())
      task.addAttribute("ContentType", serverData.receivedContentType)
    }
    task.currentStepFailed(
      message = state.failure.message,
      errorCode = "error-manifest-download",
      extraMessages = listOf()
    )

    val alert = MaterialAlertDialogBuilder(this)
    alert.setTitle(R.string.audio_book_player_error_book_open)
    alert.setMessage(R.string.audio_book_manifest_download_error)
    alert.setNeutralButton(R.string.audio_book_player_details) { dialog, _ ->
      this.openErrorPage(task.finishFailure<String>())
    }
    alert.setPositiveButton(R.string.audio_book_player_ok) { dialog, _ ->
      dialog.dismiss()
      this.close()
    }
    alert.show()
  }

  private fun onManifestLicenseChecksFailed(
    state: PlayerModelState.PlayerManifestLicenseChecksFailed
  ) {
    this.logger.error("onManifestLicenseChecksFailed: {}", state)

    val task = TaskRecorder.create()
    task.beginNewStep("Checking license…")
    task.currentStepFailed(
      message = "License checks failed.",
      errorCode = "error-manifest-license",
      extraMessages = state.messages
    )

    val alert = MaterialAlertDialogBuilder(this)
    alert.setTitle(R.string.audio_book_player_error_book_open)
    alert.setMessage(R.string.audio_book_manifest_license_error)
    alert.setNeutralButton(R.string.audio_book_player_details) { dialog, _ ->
      this.openErrorPage(task.finishFailure<String>())
    }
    alert.setPositiveButton(R.string.audio_book_player_ok) { dialog, _ ->
      dialog.dismiss()
      this.close()
    }
    alert.show()
  }

  @UiThread
  private fun onPlayerViewCommand(
    command: PlayerViewCommand
  ) {
    PlayerUIThread.checkIsUIThread()

    when (command) {
      PlayerViewCommand.PlayerViewCoverImageChanged -> {
        // Nothing to do
      }

      PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen -> {
        this.popupFragment(PlayerPlaybackRateFragment())
      }

      PlayerViewCommand.PlayerViewNavigationSleepMenuOpen -> {
        this.popupFragment(PlayerSleepTimerFragment())
      }

      PlayerViewCommand.PlayerViewNavigationTOCClose -> {
        this.switchFragment(PlayerFragment())
      }

      PlayerViewCommand.PlayerViewNavigationTOCOpen -> {
        this.switchFragment(PlayerTOCFragment())
      }

      PlayerViewCommand.PlayerViewNavigationCloseAll -> {
        this.close()
      }

      PlayerViewCommand.PlayerViewErrorsDownloadOpen -> {
        this.onOpenDownloadErrors()
      }
    }
  }

  private fun onOpenDownloadErrors() {
    val task = TaskRecorder.create()
    task.beginNewStep("Downloading book chapters…")

    try {
      val book = PlayerModel.book()
      for (e in book?.downloadTasks ?: listOf()) {
        val status = e.status
        if (status is PlayerDownloadTaskStatus.Failed) {
          val tasks = book?.downloadTasks ?: listOf()
          task.beginNewStep("Downloading ${e.playbackURI}...")
          val extraMessages =
            tasks.filterIsInstance<PlayerDownloadTaskStatus.Failed>().map { s -> s.message }
          task.currentStepFailed(
            message = status.message,
            errorCode = "error-download",
            exception = status.exception,
            extraMessages = extraMessages
          )
        }
      }
    } catch (e: Exception) {
      // Nothing to do
    }

    this.openErrorPage(task.finishFailure<String>())
  }

  private fun switchFragment(
    fragment: Fragment
  ) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.audio_book_player_fragment_holder, fragment)
      .commit()
  }

  private fun popupFragment(fragment: DialogFragment) {
    fragment.show(this.supportFragmentManager, fragment.tag)
  }
}
