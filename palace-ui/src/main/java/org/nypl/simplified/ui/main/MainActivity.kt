package org.nypl.simplified.ui.main

import android.app.ComponentCaller
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.views.PlayerMiniFragment
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerViewCommand
import org.librarysimplified.reports.Reports
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountOIDC
import org.nypl.simplified.books.time.tracking.api.TimeTrackingServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.announcements.AnnouncementsDialog
import org.nypl.simplified.ui.announcements.AnnouncementsModel
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
import org.nypl.simplified.ui.screen.ScreenEdgeToEdgeFix
import org.nypl.simplified.ui.splash.SplashFragment
import org.nypl.simplified.ui.splash.SplashModel
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_AWAITING_BOOT
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_COMPLETED
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_LIBRARY_SELECTOR
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_NOTIFICATIONS
import org.slf4j.LoggerFactory
import org.thepalaceproject.audiobook.now_playing.api.AudiobookNowPlayingServiceType
import java.net.URI
import java.util.UUID

class MainActivity : AppCompatActivity(R.layout.main_host) {
  private val logger =
    LoggerFactory.getLogger(MainActivity::class.java)

  private val backCallback =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        this@MainActivity.handleBackPressed()
      }
    }

  private lateinit var rootContainer: View
  private var fragmentNow: Fragment? = null
  private var subscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  private lateinit var nowPlaying: AudiobookNowPlayingServiceType
  private lateinit var timeTrackingService: TimeTrackingServiceType
  private lateinit var miniPlayerHolder: View
  private var playerSubscriptions: CompositeDisposable = CompositeDisposable()
  private var stopPlaybackDialog: Dialog? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(Bundle())

    val intent = this.intent
    if (intent != null) {
      this.onHandleIntent(intent)
    }

    val metrics = this.screenMetrics()
    Reports.reportScreenHeight = metrics.heightPixels
    Reports.reportScreenDPI = metrics.densityDpi

    this.rootContainer = this.findViewById(R.id.mainFragmentRoot)
    ScreenEdgeToEdgeFix.edgeToEdge(this.rootContainer)

    val services = Services.serviceDirectory()
    this.nowPlaying =
      services.requireService(AudiobookNowPlayingServiceType::class.java)
    this.timeTrackingService =
      services.requireService(TimeTrackingServiceType::class.java)
    this.miniPlayerHolder = this.findViewById(R.id.mainMiniPlayerHolder)
  }

  override fun onNewIntent(
    intent: Intent,
    caller: ComponentCaller,
  ) {
    super.onNewIntent(intent, caller)
    this.onHandleIntent(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent != null) {
      this.onHandleIntent(intent)
    }
  }

  private fun onHandleIntent(intent: Intent) {
    try {
      this.logger.debug("onHandleIntent: {}", intent)

      if (this.isAutomatedTesting(intent)) {
        this.onHandleIntentAutomatedTesting()
      }

      if (AccountOIDC.isIntentOIDC(intent)) {
        this.onHandleIntentOIDC(intent)
      } else {
        this.logger.debug("Intent does not appear to be an OIDC intent.")
      }
    } catch (e: Throwable) {
      this.logger.error("Failed to handle intent: ", e)
    }
  }

  private fun onHandleIntentOIDC(intent: Intent) {
    this.logger.debug("Parsing OIDC intent data.")

    val data =
      intent.data
    val parsed =
      AccountOIDC.parseOIDCCallback(URI.create(data.toString()))

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    when (parsed) {
      is AccountOIDC.AccountOIDCParsedCallbackLogin -> {
        this.logger.debug("OIDC intent is a login callback.")
        this.logger.debug("OIDC: Account {}", parsed.account)
        profiles.profileAccountLogin(
          ProfileAccountLoginRequest.OIDCComplete(
            accountId = parsed.account,
            accessToken = parsed.accessToken,
          ),
        )
      }

      is AccountOIDC.AccountOIDCParsedCallbackLogout -> {
        // XXX: There is nothing sensible that we can do here, currently.
        this.logger.warn("OIDC intent is a logout callback. We cannot process this!")
      }
    }
  }

  private fun onHandleIntentAutomatedTesting() {
    this.logger.warn(
      "onHandleAutomatedTesting: Unimplemented code: Requested login to testing library for automated test suite.",
    )
  }

  private fun isAutomatedTesting(intent: Intent): Boolean {
    val extras = intent.extras
    if (extras != null) {
      return extras.getBoolean("AutomatedTesting", false)
    }
    return false
  }

  private fun screenMetrics(): DisplayMetrics {
    val metrics = DisplayMetrics()
    val wm = this.windowManager
    val display = wm.getDefaultDisplay()
    display.getMetrics(metrics)
    return metrics
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()
    this.subscriptions.add(
      SplashModel.splashScreenStatus.subscribe { _, newValue ->
        this.onSplashScreenStatusChanged(newValue)
      },
    )
    this.subscriptions.add(
      this.nowPlaying.current.subscribe { _, newValue ->
        if (newValue.isPresent) {
          this.showMiniPlayer()
        } else {
          this.hideMiniPlayer()
        }
      },
    )
    this.subscriptions.add(
      this.nowPlaying.stopPlaybackDialogOpen.subscribe { _, newValue ->
        this.onStopPlaybackDialogOpenChanged(newValue)
      },
    )
    this.switchFragment(SplashFragment())

    this.playerSubscriptions =
      CompositeDisposable()
    this.playerSubscriptions.add(
      PlayerModel.viewCommands.subscribe(this::onMiniPlayerCommand),
    )

    this.onBackPressedDispatcher.addCallback(
      this,
      this.backCallback,
    )
  }

  private fun onSplashScreenStatusChanged(status: SplashScreenStatus) {
    when (status) {
      SPLASH_SCREEN_COMPLETED -> {
        this.switchFragment(MainTabsFragment())

        AnnouncementsModel.start()
        this.subscriptions.add(
          AnnouncementsModel.announcements.subscribe { _, newValue ->
            this.onAnnouncementsChanged(newValue)
          },
        )
      }

      SPLASH_SCREEN_AWAITING_BOOT,
      SPLASH_SCREEN_NOTIFICATIONS,
      SPLASH_SCREEN_LIBRARY_SELECTOR,
      -> {
        // No need to do anything.
      }
    }
  }

  private fun onAnnouncementsChanged(announcementsToAcknowledge: Map<UUID, AnnouncementsModel.EnumeratedAnnouncement>) {
    if (announcementsToAcknowledge.isNotEmpty()) {
      val tag = "ANNOUNCEMENT"
      val existing = this.supportFragmentManager.findFragmentByTag(tag)
      if (existing == null) {
        val dialog = AnnouncementsDialog()
        dialog.isCancelable = false
        dialog.show(this.supportFragmentManager, tag)
      }
    }
  }

  private fun handleBackPressed() {
    this.logger.debug("onBackPressed: Pressed")

    val current = this.fragmentNow
    if (current is MainBackButtonConsumerType) {
      val result = current.onBackButtonPressed()
      this.logger.debug("onBackPressed: Result {}", result)
      return when (result) {
        BACK_BUTTON_CONSUMED -> {
          // Fragment consumed the back button, so do nothing.
        }

        BACK_BUTTON_NOT_CONSUMED -> {
          this.finish()
        }
      }
    }

    this.logger.debug("onBackPressed: Fragment is not a back button consumer")
    return this.finish()
  }

  override fun onStop() {
    super.onStop()

    this.playerSubscriptions.dispose()
    this.subscriptions.close()
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager
      .beginTransaction()
      .replace(R.id.mainFragmentHolder, fragment)
      .commit()
  }

  private fun showMiniPlayer() {
    val existing = this.supportFragmentManager.findFragmentByTag(MINI_PLAYER_TAG)
    if (existing == null) {
      this.supportFragmentManager
        .beginTransaction()
        .add(R.id.mainMiniPlayerHolder, PlayerMiniFragment(), MINI_PLAYER_TAG)
        .commit()
    }
    this.miniPlayerHolder.visibility = View.VISIBLE
  }

  private fun hideMiniPlayer() {
    val existing = this.supportFragmentManager.findFragmentByTag(MINI_PLAYER_TAG)
    if (existing != null) {
      this.supportFragmentManager
        .beginTransaction()
        .remove(existing)
        .commit()
    }
    this.miniPlayerHolder.visibility = View.GONE
  }

  private fun onMiniPlayerCommand(command: PlayerViewCommand) {
    when (command) {
      PlayerViewCommand.PlayerViewNavigationCloseAll -> {
        this.nowPlaying.setStopPlaybackDialogOpen(true)
      }

      PlayerViewCommand.PlayerViewNavigationExpand -> {
        this.expandMiniPlayer()
      }

      else -> {
        // Cover updates and other commands are handled by the mini-player fragment.
      }
    }
  }

  private fun expandMiniPlayer() {
    val ref = this.nowPlaying.current.get()
    if (ref.isPresent) {
      this.nowPlaying.openForPlayback(ref.get(), launchUI = true)
    }
  }

  private fun onStopPlaybackDialogOpenChanged(open: Boolean) {
    if (open) {
      this.showCloseConfirmationDialog()
    } else {
      this.dismissCloseConfirmationDialog()
    }
  }

  private fun showCloseConfirmationDialog() {
    val existing = this.stopPlaybackDialog
    if (existing != null && existing.isShowing) {
      return
    }
    val dialog =
      MaterialAlertDialogBuilder(this)
        .setMessage(R.string.audioBookMiniPlayerCloseConfirm)
        .setNegativeButton(R.string.audioBookMiniPlayerCloseCancel) { _, _ ->
          this.nowPlaying.setStopPlaybackDialogOpen(false)
        }.setPositiveButton(R.string.audioBookMiniPlayerCloseStop) { _, _ ->
          this.nowPlaying.setStopPlaybackDialogOpen(false)
          this.closeAudiobook()
        }.show()
    dialog.setOnCancelListener {
      this.nowPlaying.setStopPlaybackDialogOpen(false)
    }
    this.stopPlaybackDialog = dialog
  }

  private fun dismissCloseConfirmationDialog() {
    val dialog = this.stopPlaybackDialog
    if (dialog != null) {
      dialog.dismiss()
      this.stopPlaybackDialog = null
    }
  }

  private fun closeAudiobook() {
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
      this.nowPlaying.setCurrent(null)
    } catch (e: Exception) {
      this.logger.debug("Failed to clear the current audiobook: ", e)
    }
  }

  private companion object {
    private const val MINI_PLAYER_TAG = "miniPlayer"
  }
}
