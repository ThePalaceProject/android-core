package org.librarysimplified.ui.navigation.tabs

import android.content.Context
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.LabelVisibilityMode
import com.io7m.junreachable.UnreachableCodeException
import com.pandora.bottomnavigator.BottomNavigator
import org.joda.time.DateTime
import org.librarysimplified.ui.tabs.R
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.settings.SettingsMainFragment3
import org.slf4j.LoggerFactory

object BottomNavigators {

  private val logger = LoggerFactory.getLogger(BottomNavigators::class.java)

  /**
   * Create a new tabbed navigation controller. The controller will load fragments into the
   * fragment container specified by [fragmentContainerId], using the Pandora BottomNavigator
   * view [navigationView].
   */

  fun create(
    fragment: Fragment,
    @IdRes fragmentContainerId: Int,
    navigationView: BottomNavigationView,
    accountProviders: AccountProviderRegistryType,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
  ): BottomNavigator {
    logger.debug("creating bottom navigator")

    val context =
      fragment.requireContext()

    val navigator =
      BottomNavigator.onCreate(
        fragmentContainer = fragmentContainerId,
        bottomNavigationView = navigationView,
        rootFragmentsFactory = mapOf(
          R.id.tabCatalog to {
            createCatalogFragment(
              id = R.id.tabCatalog
            )
          },
          R.id.tabBooks to {
            createBooksFragment(
              context = context,
              id = R.id.tabBooks,
              profilesController = profilesController,
              settingsConfiguration = settingsConfiguration,
              defaultProvider = accountProviders.defaultProvider
            )
          },
          R.id.tabHolds to {
            createHoldsFragment(
              context = context,
              id = R.id.tabHolds,
              profilesController = profilesController,
              settingsConfiguration = settingsConfiguration,
              defaultProvider = accountProviders.defaultProvider
            )
          },
          R.id.tabSettings to {
            createSettingsFragment(R.id.tabSettings)
          }
        ),
        defaultTab = R.id.tabCatalog,
        fragment = fragment,
        instanceOwner = fragment
      )

    navigationView.labelVisibilityMode = LabelVisibilityMode.LABEL_VISIBILITY_LABELED
    return navigator
  }

  private fun currentAge(
    profilesController: ProfilesControllerType
  ): Int {
    return try {
      val profile = profilesController.profileCurrent()
      profile.preferences().dateOfBirth?.yearsOld(DateTime.now()) ?: 1
    } catch (e: Exception) {
      logger.debug("could not retrieve profile age: ", e)
      1
    }
  }

  private fun pickDefaultAccount(
    profilesController: ProfilesControllerType,
    defaultProvider: AccountProviderType
  ): AccountType {
    val profile = profilesController.profileCurrent()
    val mostRecentId = profile.preferences().mostRecentAccount
    if (mostRecentId != null) {
      try {
        return profile.account(mostRecentId)
      } catch (e: Exception) {
        logger.debug("stale account: ", e)
      }
    }

    val accounts = profile.accounts().values
    return when {
      accounts.size > 1 -> {
        // Return the first account created from a non-default provider
        accounts.first { it.provider.id != defaultProvider.id }
      }
      accounts.size == 1 -> {
        // Return the first account
        accounts.first()
      }
      else -> {
        // There should always be at least one account
        throw UnreachableCodeException()
      }
    }
  }

  private fun createSettingsFragment(id: Int): Fragment {
    logger.debug("[{}]: creating settings fragment", id)
    return SettingsMainFragment3()
  }

  private fun createHoldsFragment(
    context: Context,
    id: Int,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
    defaultProvider: AccountProviderType
  ): Fragment {
    logger.debug("[{}]: creating holds fragment", id)
    return Fragment()
  }

  private fun createBooksFragment(
    context: Context,
    id: Int,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
    defaultProvider: AccountProviderType
  ): Fragment {
    logger.debug("[{}]: creating books fragment", id)
    return Fragment()
  }

  private fun createCatalogFragment(
    id: Int
  ): Fragment {
    logger.debug("[{}]: creating catalog fragment", id)
    return Fragment()
  }
}
