package org.nypl.simplified.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.splash.SplashFragment
import org.nypl.simplified.ui.splash.SplashModel

class MainActivity : AppCompatActivity(R.layout.main_host) {

  private var fragmentNow: Fragment? = null
  private var subscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
    super.onCreate(Bundle())
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
      SplashModel.SplashScreenStatus.SPLASH_SCREEN_IN_PROGRESS -> {
        // No need to do anything.
      }

      SplashModel.SplashScreenStatus.SPLASH_SCREEN_COMPLETED -> {
        this.switchFragment(MainTabsFragment())
      }
    }
  }

  @Deprecated("This method has been deprecated by clueless \"engineers\".")
  override fun onBackPressed() {
    // Do nothing, currently
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
