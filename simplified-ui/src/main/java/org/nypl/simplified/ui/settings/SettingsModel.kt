package org.nypl.simplified.ui.settings

import org.nypl.simplified.profiles.controller.api.ProfilesControllerType

object SettingsModel {

  private var debugClicks = 0

  fun onClickVersion(
    profiles: ProfilesControllerType
  ) {
    ++this.debugClicks
    if (this.debugClicks >= 7) {
      this.debugClicks = 0
      profiles.profileUpdate { d ->
        d.copy(preferences = d.preferences.copy(
          showDebugSettings = !d.preferences.showDebugSettings)
        )
      }
    }
  }

  fun showDebugSettings(
    profiles: ProfilesControllerType
  ): Boolean {
    return profiles
      .profileCurrent()
      .preferences()
      .showDebugSettings
  }
}
