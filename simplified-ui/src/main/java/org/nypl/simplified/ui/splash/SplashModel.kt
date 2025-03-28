package org.nypl.simplified.ui.splash

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeSubscriptionType
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.main.MainAttributes
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService

object SplashModel {

  private val logger =
    LoggerFactory.getLogger(SplashModel::class.java)

  private val accountProvidersActual: AttributeType<List<AccountProviderDescription>> =
    MainAttributes.attributes.withValue(listOf())
  private val accountProvidersActualUI: AttributeType<List<AccountProviderDescription>> =
    MainAttributes.attributes.withValue(listOf())

  init {
    MainAttributes.wrapAttribute(this.accountProvidersActual, this.accountProvidersActualUI)
  }

  val accountProviders: AttributeReadableType<List<AccountProviderDescription>>
    get() = this.accountProvidersActualUI

  private val splashScreenStatusActual =
    MainAttributes.attributes.withValue(SplashScreenStatus.SPLASH_SCREEN_IN_PROGRESS)

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

  fun accountProvidersLoad(
    executor: ExecutorService,
    registry: AccountProviderRegistryType
  ): AttributeSubscriptionType {
    executor.execute { registry.refresh(false) }
    return registry.statusAttribute.subscribe { _, status ->
      when (status) {
        AccountProviderRegistryStatus.Idle -> {
          this.accountProvidersActual.set(
            registry.accountProviderDescriptions().values
              .sortedBy { d -> d.title }
          )
        }

        AccountProviderRegistryStatus.Refreshing -> {
          this.accountProvidersActual.set(listOf())
        }
      }
    }
  }
}
