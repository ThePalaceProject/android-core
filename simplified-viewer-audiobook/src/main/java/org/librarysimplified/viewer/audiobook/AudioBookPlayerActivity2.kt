package org.librarysimplified.viewer.audiobook

import android.os.Bundle
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate2
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerBookmark
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
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
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
import org.slf4j.LoggerFactory

class AudioBookPlayerActivity2 : AppCompatActivity(R.layout.audio_book_player_base) {

  private val logger =
    LoggerFactory.getLogger(AudioBookPlayerActivity2::class.java)

  private lateinit var bookmarkService: BookmarkServiceType

  private val playerExtensions: List<PlayerExtensionType> = listOf()
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

    this.bookmarkService =
      services.requireService(BookmarkServiceType::class.java)
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
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
            PlayerModel.closeBookOrDismissError()
            Unit
          }

          is PlayerTOCFragment -> {
            this.switchFragment(PlayerFragment())
          }
        }
      }

      is AudioBookLoadingFragment2 -> {
        PlayerModel.closeBookOrDismissError()
        super.onBackPressed()
      }

      null -> {
        PlayerModel.closeBookOrDismissError()
        super.onBackPressed()
      }

      else -> {
        throw IllegalStateException("Unrecognized fragment: $f")
      }
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
            offsetMilliseconds = event.offsetMilliseconds,
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
      }

      is PlayerEventDeleteBookmark -> {
        val parameters =
          AudioBookViewerModel.parameters ?: return

        val playerBookmark = event.bookmark
        this.bookmarkService.bookmarkDelete(
          accountID = parameters.accountID,
          bookmark = AudioBookBookmarks.fromPlayerBookmark(
            feedEntry = parameters.opdsEntry,
            deviceId = "null",
            source = playerBookmark
          ),
          ignoreRemoteFailures = true,
        )
      }

      is PlayerEventError -> {
        // Nothing yet...
      }

      PlayerEventManifestUpdated -> {
        // Nothing yet...
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
        this.switchFragment(AudioBookLoadingFragment2())
      }

      is PlayerModelState.PlayerManifestDownloadFailed -> {
        this.onManifestDownloadFailed(state)
      }

      is PlayerModelState.PlayerManifestLicenseChecksFailed -> {
        this.onManifestLicenseChecksFailed(state)
      }

      is PlayerModelState.PlayerManifestOK -> {
        PlayerModel.openPlayerForManifest(
          context = this.application,
          userAgent = PlayerUserAgent(bookParameters.userAgent),
          extensions = this.playerExtensions,
          manifest = state.manifest
        )
      }

      is PlayerModelState.PlayerManifestParseFailed -> {
        this.onManifestParseFailed(state)
      }

      is PlayerModelState.PlayerOpen -> {
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
        if (bookmarkLastRead != null) {
          PlayerModel.movePlayheadTo(bookmarkLastRead.position)
        }

        this.switchFragment(PlayerFragment())
      }

      PlayerModelState.PlayerManifestInProgress -> {
        this.switchFragment(AudioBookLoadingFragment2())
      }
    }
  }

  private fun onManifestParseFailed(
    state: PlayerModelState.PlayerManifestParseFailed
  ) {
    this.logger.error("onManifestParseFailed: {}", state)
  }

  private fun onBookOpenFailed(
    state: PlayerModelState.PlayerBookOpenFailed
  ) {
    this.logger.error("onBookOpenFailed: {}", state)
  }

  private fun onManifestDownloadFailed(
    state: PlayerModelState.PlayerManifestDownloadFailed
  ) {
    this.logger.error("onManifestDownloadFailed: {}", state)
  }

  private fun onManifestLicenseChecksFailed(
    state: PlayerModelState.PlayerManifestLicenseChecksFailed
  ) {
    this.logger.error("onManifestLicenseChecksFailed: {}", state)
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
    }
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
