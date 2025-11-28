package org.nypl.simplified.ui.main

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.reports.Reports
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.announcements.AnnouncementsDialog
import org.nypl.simplified.ui.announcements.AnnouncementsModel
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
import org.nypl.simplified.ui.screen.ScreenEdgeToEdgeFix
import org.nypl.simplified.ui.splash.SplashFragment
import org.nypl.simplified.ui.splash.SplashModel
import org.slf4j.LoggerFactory
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

    val metrics = this.screenMetrics()
    Reports.reportScreenHeight = metrics.heightPixels
    Reports.reportScreenDPI = metrics.densityDpi

    this.rootContainer = this.findViewById(R.id.mainFragmentRoot)
    ScreenEdgeToEdgeFix.edgeToEdge(this.rootContainer)
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
    status: SplashModel.SplashScreenStatus
  ) {
    when (status) {
      SplashModel.SplashScreenStatus.SPLASH_SCREEN_COMPLETED -> {
        this.switchFragment(MainTabsFragment())

        AnnouncementsModel.start()
        AnnouncementsModel.announcements.subscribe { _, newValue ->
          this.onAnnouncementsChanged(newValue)
        }
      }

      SplashModel.SplashScreenStatus.SPLASH_SCREEN_AWAITING_BOOT,
      SplashModel.SplashScreenStatus.SPLASH_SCREEN_NOTIFICATIONS,
      SplashModel.SplashScreenStatus.SPLASH_SCREEN_TUTORIAL,
      SplashModel.SplashScreenStatus.SPLASH_SCREEN_LIBRARY_SELECTOR -> {
        // No need to do anything.
      }
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
}
