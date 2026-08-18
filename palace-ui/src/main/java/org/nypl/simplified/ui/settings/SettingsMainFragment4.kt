package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType

class SettingsMainFragment4 :
  Fragment(R.layout.settings2),
  MainBackButtonConsumerType {
  private lateinit var settings2AboutPalace: SettingsTextView
  private lateinit var settings2AddRemoveAccounts: SettingsTextView
  private lateinit var settings2AppVersion: SettingsTextView
  private lateinit var settings2AudiobooksSkipBackward: SettingsTextView
  private lateinit var settings2AudiobooksSkipForward: SettingsTextView
  private lateinit var settings2BatteryOptimizer: SettingsTextView
  private lateinit var settings2Commit: SettingsTextView
  private lateinit var settings2Debug: SettingsTextView
  private lateinit var settings2Licenses: SettingsTextView
  private lateinit var settings2NetworkDownloadOnWifiEnabled: SettingsToggleView
  private lateinit var settings2NotificationsEnableDisable: SettingsTextView
  private lateinit var settings2PrivacyPolicy: SettingsTextView
  private lateinit var settings2UserAgreement: SettingsTextView

  companion object : ScreenDefinitionFactoryType<Unit, SettingsMainFragment4> {
    private class ScreenSettingsMain : ScreenDefinitionType<Unit, SettingsMainFragment4> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        // Not parameters
      }

      override fun fragment(): SettingsMainFragment4 {
        return SettingsMainFragment4()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsMainFragment4> {
      return ScreenSettingsMain()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    return MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.settings2AddRemoveAccounts =
      view.findViewById(R.id.settings2AddRemoveAccounts)
    this.settings2NotificationsEnableDisable =
      view.findViewById(R.id.settings2NotificationsEnableDisable)
    this.settings2NetworkDownloadOnWifiEnabled =
      view.findViewById(R.id.settings2NetworkDownloadOnWifiEnabled)
    this.settings2BatteryOptimizer =
      view.findViewById(R.id.settings2BatteryOptimizer)
    this.settings2AudiobooksSkipForward =
      view.findViewById(R.id.settings2AudiobooksSkipForward)
    this.settings2AudiobooksSkipBackward =
      view.findViewById(R.id.settings2AudiobooksSkipBackward)
    this.settings2AboutPalace =
      view.findViewById(R.id.settings2AboutPalace)
    this.settings2UserAgreement =
      view.findViewById(R.id.settings2UserAgreement)
    this.settings2Licenses =
      view.findViewById(R.id.settings2Licenses)
    this.settings2PrivacyPolicy =
      view.findViewById(R.id.settings2PrivacyPolicy)
    this.settings2AppVersion =
      view.findViewById(R.id.settings2AppVersion)
    this.settings2Commit =
      view.findViewById(R.id.settings2Commit)
    this.settings2Debug =
      view.findViewById(R.id.settings2Debug)
  }
}
