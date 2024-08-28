package org.librarysimplified.viewer.audiobook

import android.os.Bundle
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
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.books.time.tracking.TimeTrackingServiceType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.errorpage.ErrorPageFragment
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.slf4j.LoggerFactory
import java.net.URI

class AudioBookPlayerActivity2 : AppCompatActivity(R.layout.audio_book_player_base) {

  private val logger =
    LoggerFactory.getLogger(AudioBookPlayerActivity2::class.java)

  private lateinit var bookmarkService: BookmarkServiceType
  private lateinit var buildConfig: BuildConfigurationServiceType
  private lateinit var coverService: BookCoverProviderType
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
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))

    this.loadCoverImage()
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
      this.logger.error("Failed to close book: ", e)
    }

    try {
      this.timeTrackingService.stopTracking()
    } catch (e: Exception) {
      this.logger.error("Failed to stop time tracking: ", e)
    }

    try {
      this.finish()
    } catch (e: Exception) {
      this.logger.error("Failed to finish activity: ", e)
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
      is PlayerEventPlaybackRateChanged,
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

      PlayerEventManifestUpdated -> {
        // Nothing yet...
      }
    }

    try {
      this.timeTrackingService.onPlayerEventReceived(event)
    } catch (e: Exception) {
      this.logger.error("Failed to submit event to time tracking service: ", e)
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
        this.timeTrackingService.stopTracking()
        this.switchFragment(AudioBookLoadingFragment2())
      }

      is PlayerModelState.PlayerManifestDownloadFailed -> {
        this.onManifestDownloadFailed(state)
      }

      is PlayerModelState.PlayerManifestLicenseChecksFailed -> {
        this.onManifestLicenseChecksFailed(state)
      }

      is PlayerModelState.PlayerManifestOK -> {
        this.timeTrackingService.startTimeTracking(
          accountID = bookParameters.accountID,
          bookId = bookParameters.opdsEntry.id,
          libraryId = bookParameters.accountProviderID.toString(),
          timeTrackingUri = bookParameters.opdsEntry.timeTrackingUri.getOrNull()
        )

        /*
         * XXX: This shouldn't really be a blocking call to get()
         * The bookmarks service should expose an always-up-to-date readable set of bookmarks.
         */

        val bookmarks =
          this.bookmarkService.bookmarkLoad(
            accountID = bookParameters.accountID,
            book = bookParameters.bookID
          ).get()

        val bookmarksConverted =
          bookmarks.bookmarks.mapNotNull(AudioBookBookmarks::toPlayerBookmark)
        val bookmarkLastRead =
          bookmarks.lastRead?.let { b -> AudioBookBookmarks.toPlayerBookmark(b) }

        PlayerBookmarkModel.setBookmarks(bookmarksConverted)

        val initialPosition =
          if (bookmarkLastRead != null) {
            this.logger.debug("Restoring last-read position: {}", bookmarkLastRead.position)
            bookmarkLastRead.position
          } else {
            null
          }

        PlayerModel.openPlayerForManifest(
          context = this.application,
          userAgent = PlayerUserAgent(bookParameters.userAgent),
          manifest = state.manifest,
          fetchAll = true,
          initialPosition = initialPosition,
          bookFile = bookParameters.file,
          bookCredentials = bookParameters.drmInfo.playerCredentials()
        )
      }

      is PlayerModelState.PlayerManifestParseFailed -> {
        this.onManifestParseFailed(state)
      }

      is PlayerModelState.PlayerOpen -> {
        this.loadCoverImage()
        this.switchFragment(PlayerFragment())
      }

      PlayerModelState.PlayerManifestInProgress -> {
        this.switchFragment(AudioBookLoadingFragment2())
      }
    }
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
    state.failure.mapIndexed { index, error ->
      this.logger.error("{}:{}: {}", error.line, error.column, error.message)
      task.addAttribute(
        "Parse Error [$index]",
        "${error.line}:${error.column}: ${error.message}"
      )
    }
    task.currentStepFailed("Parsing failed.", "error-manifest-parse")

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
    this.switchFragment(
      ErrorPageFragment.create(
        ErrorPageParameters(
          emailAddress = this.buildConfig.supportErrorReportEmailAddress,
          body = "",
          subject = "[palace-audiobook-error-report]",
          attributes = sortedMapOf(),
          taskSteps = result.steps
        )
      )
    )
  }

  private fun onBookOpenFailed(
    state: PlayerModelState.PlayerBookOpenFailed
  ) {
    this.logger.error("onBookOpenFailed: {}", state)

    val task = TaskRecorder.create()
    task.beginNewStep("Opening book…")
    task.currentStepFailed(state.message, "error-book-open")

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
      task.addAttribute("URI", serverData.uri.toString())
      task.addAttribute("Code", serverData.code.toString())
      task.addAttribute("ContentType", serverData.receivedContentType)
    }
    task.currentStepFailed(state.failure.message, "error-manifest-download")

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
    task.currentStepFailed("License checks failed.", "error-manifest-license")

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
      for (e in book.downloadTasks) {
        val status = e.status
        if (status is PlayerDownloadTaskStatus.Failed) {
          task.beginNewStep("Downloading ${e.playbackURI}...")
          task.currentStepFailed(status.message, "error-download", status.exception)
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
