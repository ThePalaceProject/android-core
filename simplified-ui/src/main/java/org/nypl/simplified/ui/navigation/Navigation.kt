package org.nypl.simplified.ui.navigation

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.Attributes
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory

object Navigation {

  private val logger =
    LoggerFactory.getLogger(Navigation::class.java)

  private val attributes =
    Attributes.create { ex -> this.logger.error("Uncaught exception in attribute: ", ex) }

  private val splashScreenStatusActual =
    this.attributes.withValue(SplashScreenStatus.SPLASH_SCREEN_IN_PROGRESS)

  /**
   * An attribute that publishes the splash screen status. Updates are guaranteed to be
   * delivered on the UI thread.
   */

  val splashScreenStatus: AttributeReadableType<SplashScreenStatus>
    get() = this.splashScreenStatusActual

  /**
   * The status of the splash screen.
   */

  enum class SplashScreenStatus {

    /**
     * The splash screen is still in progress. Either the application hasn't finished booting up,
     * or the user hasn't finished going through the whole onboarding process.
     */

    SPLASH_SCREEN_IN_PROGRESS,

    /**
     * The splash screen has fully completed. The user has finished selecting a library, viewing
     * the tutorial, etc, and the main catalog view should now be shown.
     */

    SPLASH_SCREEN_COMPLETED
  }

  fun splashScreenCompleted() {
    UIThread.checkIsUIThread()
    this.splashScreenStatusActual.set(SplashScreenStatus.SPLASH_SCREEN_COMPLETED)
  }
}
