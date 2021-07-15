package org.nypl.simplified.ui.settings

sealed class SettingsMainEvent {

  /**
   * The settings screen wants to open the "about" screen.
   */

  data class OpenAbout(
    val title: String,
    val url: String,
  ) : SettingsMainEvent()

  /**
   * The settings screen wants to open the list of added accounts.
   */

  object OpenAccountList : SettingsMainEvent()

  /**
   * The settings screen wants to open the "acknowledgements" screen.
   */

  data class OpenAcknowledgments(
    val title: String,
    val url: String,
  ) : SettingsMainEvent()

  /**
   * The settings screen wants to open debug options.
   */

  object OpenDebugOptions : SettingsMainEvent()

  /**
   * The settings screen wants to open the "license" screen.
   */

  data class OpenLicense(
    val title: String,
    val url: String,
  ) : SettingsMainEvent()

  /**
   * The settings screen wants to open the "FAQ" screen.
   */

  data class OpenFAQ(
    val title: String,
    val url: String,
  ) : SettingsMainEvent()

  /**
   * The settings screen wants to open the "EULA" screen.
   */

  data class OpenEULA(
    val title: String,
    val url: String,
  ) : SettingsMainEvent()

  /**
   * The settings screen wants to open the "Privacy" screen.
   */

  data class OpenPrivacy(
    val title: String,
    val url: String,
  ) : SettingsMainEvent()
}
