package org.nypl.simplified.ui.settings

import org.nypl.simplified.navigation.api.NavigationControllerType
import org.nypl.simplified.ui.accounts.AccountNavigationControllerType

/**
 * Navigation functions for the settings screens.
 */

interface SettingsNavigationControllerType :
  NavigationControllerType,
  AccountNavigationControllerType {

  /**
   * The settings screen wants to open the "about" section.
   */

  fun openSettingsAbout(title: String, url: String)

  /**
   * The settings screen wants to open the list of accounts.
   */

  fun openSettingsAccounts()

  /**
   * The settings screen wants to open the "acknowledgements" section.
   */

  fun openSettingsAcknowledgements(title: String, url: String)

  /**
   * The settings screen wants to open the "EULA" section.
   */

  fun openSettingsEULA(title: String, url: String)

  /**
   * The settings screen wants to open the "FAQ" section.
   */

  fun openSettingsFaq(title: String, url: String)

  /**
   * The settings screen wants to open the "license" section.
   */

  fun openSettingsLicense(title: String, url: String)

  /**
   * The settings screen wants to open the version screen.
   */

  fun openSettingsVersion()

  /**
   * The settings screen wants to open the custom OPDS creation form.
   */

  fun openSettingsCustomOPDS()
}
