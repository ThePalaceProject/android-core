package org.nypl.simplified.ui.main

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.reports.Reports
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountOIDC
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.announcements.AnnouncementsDialog
import org.nypl.simplified.ui.announcements.AnnouncementsModel
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
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
import org.nypl.simplified.viewer.api.Viewers
import org.nypl.simplified.viewer.spi.ViewerParameters
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID

class MainActivity : AppCompatActivity(R.layout.main_host) {

  private val logger =
    LoggerFactory.getLogger(MainActivity::class.java)

  private lateinit var rootContainer: View
  private var fragmentNow: Fragment? = null
  private var subscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
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
  }

  override fun onNewIntent(
    intent: Intent,
    caller: ComponentCaller
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

  private fun onHandleIntent(
    intent: Intent
  ) {
    try {
      if (this.isAutomatedTesting(intent)) {
        this.onHandleAutomatedTesting()
      }

      if (AccountOIDC.isIntentOIDC(intent)) {
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
            profiles.profileAccountLogin(
              ProfileAccountLoginRequest.OIDCComplete(
                accountId = parsed.account,
                accessToken = parsed.accessToken
              )
            )
          }

          is AccountOIDC.AccountOIDCParsedCallbackLogout -> {
            // XXX: There is nothing sensible that we can do here, currently.
          }
        }
      }
    } catch (e: Throwable) {
      this.logger.error("Failed to handle intent: ", e)
    }
  }

  private fun onHandleAutomatedTesting() {
    this.logger.warn("Unimplemented code: Requested login to testing library for automated test suite.")
  }

  private fun isAutomatedTesting(
    intent: Intent
  ): Boolean {
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
      }
    )
    this.switchFragment(SplashFragment())
  }

  private fun onSplashScreenStatusChanged(
    status: SplashScreenStatus
  ) {
    when (status) {
      SPLASH_SCREEN_COMPLETED -> {
        this.switchFragment(MainTabsFragment())

        AnnouncementsModel.start()
        this.subscriptions.add(
          AnnouncementsModel.announcements.subscribe { _, newValue ->
            this.onAnnouncementsChanged(newValue)
          }
        )

        val sub =
          MainBackgroundBookOpenRequests.requestStream.subscribe { bookRequest ->
            this.openBookFromBackgroundRequest(bookRequest)
          }

        this.subscriptions.add(AutoCloseable { sub.dispose() })
      }

      SPLASH_SCREEN_AWAITING_BOOT,
      SPLASH_SCREEN_NOTIFICATIONS,
      SPLASH_SCREEN_LIBRARY_SELECTOR -> {
        // No need to do anything.
      }
    }
  }

  @UiThread
  private fun openBookFromBackgroundRequest(
    bookRequest: MainBackgroundBookOpenRequests.BookOpenRequest
  ) {
    this.logger.debug("Received a request to open: {}", bookRequest)
    UIThread.checkIsUIThread()

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val profile =
      profiles.profileCurrent()

    val viewerParameters =
      ViewerParameters(
        flags = mapOf(),
        viewerID = bookRequest.playerID,
        onLoginRequested = { accountID ->
          try {
            this.logger.debug("Bringing main activity to foreground...")
            val activity = this
            val intent = Intent(activity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            activity.startActivity(intent)

            this.logger.debug("Showing login dialog...")
            MainNavigation.showLoginDialog(profile.account(accountID))
          } catch (e: Throwable) {
            this.logger.error("Unable to open login dialog: ", e)
          }
        }
      )

    try {
      Viewers.openViewer(
        context = this,
        preferences = viewerParameters,
        book = bookRequest.book,
        format = bookRequest.bookFormat
      )
    } catch (e: Throwable) {
      this.openErrorForBookException(bookRequest.book, e)
    }
  }

  private fun onAnnouncementsChanged(
    announcementsToAcknowledge: Map<UUID, AnnouncementsModel.EnumeratedAnnouncement>
  ) {
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

  @Deprecated("This method has been deprecated by clueless \"engineers\".")
  override fun onBackPressed() {
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

    this.subscriptions.close()
  }

  private fun switchFragment(
    fragment: Fragment
  ) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.mainFragmentHolder, fragment)
      .commit()
  }

  private fun openErrorForBookException(
    book: Book,
    e: Throwable
  ) {
    try {
      val services =
        Services.serviceDirectory()
      val buildConfig =
        services.requireService(BuildConfigurationServiceType::class.java)
      val profiles =
        services.requireService(ProfilesControllerType::class.java)
      val account =
        profiles.profileCurrent()
          .account(book.account)

      val task = TaskRecorder.create()
      task.beginNewStep("Attempting to open book...")
      task.addAttribute("BookID", book.entry.id)
      task.addAttribute("LibraryID", account.provider.id.toString())
      task.addAttribute("Library", account.provider.displayName)
      task.currentStepFailed(
        message = e.message ?: e.javaClass.name,
        errorCode = "error-book-open-failed",
        exception = e,
        extraMessages = listOf()
      )
      val error = task.finishFailure<Unit>()

      MainNavigation.openErrorPage(
        activity = this,
        parameters = ErrorPageParameters(
          emailAddress = buildConfig.supportErrorReportEmailAddress,
          body = "",
          subject = "[palace-error-report]",
          attributes = error.attributes.toSortedMap(),
          taskSteps = error.steps
        )
      )
    } catch (e: Throwable) {
      this.logger.error("Failed to open error page: ", e)
    }
  }
}
