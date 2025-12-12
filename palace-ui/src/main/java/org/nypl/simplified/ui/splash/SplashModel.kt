package org.nypl.simplified.ui.splash

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeSubscriptionType
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountProviderDescriptionComparator
import org.nypl.simplified.ui.main.MainAttributes

object SplashModel {

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
    MainAttributes.attributes.withValue(SplashScreenStatus.SPLASH_SCREEN_AWAITING_BOOT)

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
     * The splash screen is awaiting the boot process.
     */

    SPLASH_SCREEN_AWAITING_BOOT,

    /**
     * The splash screen is asking the user if they want to enable notifications.
     */

    SPLASH_SCREEN_NOTIFICATIONS,

    /**
     * The user is being asked to select a library.
     */

    SPLASH_SCREEN_LIBRARY_SELECTOR,

    /**
     * The splash screen has fully completed. The user has finished selecting a library, viewing
     * the tutorial, etc, and the main catalog view should now be shown.
     */

    SPLASH_SCREEN_COMPLETED
  }

  fun accountProvidersLoad(
    buildConfig: BuildConfigurationServiceType,
    registry: AccountProviderRegistryType
  ): AttributeSubscriptionType {
    registry.refreshAsync(includeTestingLibraries = false)
    return registry.statusAttribute.subscribe { _, status ->
      when (status) {
        AccountProviderRegistryStatus.Idle -> {
          this.accountProvidersActual.set(
            registry.accountProviderDescriptions().values
              .sortedWith(AccountProviderDescriptionComparator(buildConfig))
          )
        }

        AccountProviderRegistryStatus.Refreshing -> {
          this.accountProvidersActual.set(listOf())
        }
      }
    }
  }

  fun splashScreenCompleteNotifications(
    profiles: ProfilesControllerType
  ) {
    this.splashScreenStatusActual.set(SplashScreenStatus.SPLASH_SCREEN_LIBRARY_SELECTOR)

    profiles.profileUpdate { description ->
      description.copy(
        preferences = description.preferences.copy(hasSeenNotificationScreen = true)
      )
    }
  }

  fun splashScreenCompleteLibrarySelection(
    profiles: ProfilesControllerType
  ) {
    this.splashScreenStatusActual.set(SplashScreenStatus.SPLASH_SCREEN_COMPLETED)

    profiles.profileUpdate { description ->
      description.copy(
        preferences = description.preferences.copy(hasSeenLibrarySelectionScreen = true)
      )
    }
  }

  fun splashScreenCompleteTutorial(
    profiles: ProfilesControllerType
  ) {
    this.splashScreenStatusActual.set(SplashScreenStatus.SPLASH_SCREEN_LIBRARY_SELECTOR)
  }

  fun splashScreenCompleteBoot() {
    this.splashScreenStatusActual.set(SplashScreenStatus.SPLASH_SCREEN_NOTIFICATIONS)
  }

  fun userHasSeenLibrarySelection(
    profiles: ProfilesControllerType
  ): Boolean {
    val profile =
      profiles.profileCurrent()
    val preferences =
      profile.preferences()

    return preferences.hasSeenLibrarySelectionScreen
  }

  fun userHasCompletedTutorial(
    profiles: ProfilesControllerType
  ): Boolean {
    return this.userHasSeenLibrarySelection(profiles)
  }

  fun userHasSeenNotifications(
    profiles: ProfilesControllerType
  ): Boolean {
    val profile =
      profiles.profileCurrent()
    val preferences =
      profile.preferences()

    return preferences.hasSeenNotificationScreen
  }
}
