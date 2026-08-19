package org.nypl.simplified.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.api.LSHTTPNetworkAccessType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.main.MainNotifications
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory
import org.thepalaceproject.palace.battery.BatteryModel
import java.util.concurrent.TimeUnit

class SettingsMainFragment4 :
  Fragment(R.layout.settings2),
  MainBackButtonConsumerType {
  private val logger =
    LoggerFactory.getLogger(SettingsMainFragment4::class.java)

  private lateinit var networkAccess: LSHTTPNetworkAccessType
  private lateinit var subscriptions: CloseableCollectionType<ClosingResourceFailedException>
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

  override fun onStart() {
    super.onStart()

    val services =
      Services.serviceDirectory()
    val profileEvents =
      services.requireService(SettingsProfileEvents::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    this.networkAccess =
      services.requireService(LSHTTPNetworkAccessType::class.java)

    this.subscriptions =
      CloseableCollection.create()

    val profileSub =
      profileEvents.events.subscribe({ e ->
        this.onProfileEvent(profiles, e)
      })
    this.subscriptions.add(AutoCloseable { profileSub.dispose() })
    this.subscriptions.add(
      BatteryModel.batteryOptimizerStatus.subscribe { _, valueNew ->
        this.onBatteryOptimizerStatusChanged(valueNew)
      }
    )

    try {
      this.configureDebug()
    } catch (e: Throwable) {
      this.logger.debug("Error configuring debug menu: ", e)
    }

    this.configureBuild()
    this.configureNetwork(
      profiles = profiles,
      profilePrefs = profiles.profileCurrent().preferences()
    )
    this.configureNotifications()
    this.configureBattery()
    BatteryModel.batteryOptimizerCheck()
  }

  private fun configureBuild() {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    this.settings2Commit.textSummary.text =
      buildConfig.vcsCommit
    this.settings2Commit.setOnClickListener {
      SettingsModel.onClickVersion(profiles)
    }
  }

  private fun configureBattery() {
    this.settings2BatteryOptimizer.setOnClickListener {
      val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      this.requireActivity().startActivity(intent)
    }
  }

  private fun onBatteryOptimizerStatusChanged(enabled: Boolean) {
    UIThread.checkIsUIThread()

    if (enabled) {
      this.settings2BatteryOptimizer.textSummary.text =
        this.getString(R.string.settingsBatteryOptimizerEnabledSummary)
      this.settings2BatteryOptimizer.textTitle.text =
        this.getString(R.string.settingsBatteryOptimizerEnabled)
    } else {
      this.settings2BatteryOptimizer.textSummary.text =
        this.getString(R.string.settingsBatteryOptimizerDisabledSummary)
      this.settings2BatteryOptimizer.textTitle.text =
        this.getString(R.string.settingsBatteryOptimizerDisabled)
    }
  }

  private fun onProfileEvent(
    profiles: ProfilesControllerType,
    e: ProfileEvent
  ) {
    if (e is ProfileUpdated) {
      this.configureDebug()
      this.configureNetwork(
        profiles = profiles,
        profilePrefs = profiles.profileCurrent().preferences()
      )
    }
  }

  private fun configureNetwork(
    profiles: ProfilesControllerType,
    profilePrefs: ProfilePreferences
  ) {
    val isOnlyWifi = profilePrefs.downloadOnlyOnWIFI
    if (isOnlyWifi) {
      this.settings2NetworkDownloadOnWifiEnabled.toggle.isChecked = true
    } else {
      this.settings2NetworkDownloadOnWifiEnabled.toggle.isChecked = false
    }

    this.settings2NetworkDownloadOnWifiEnabled.setOnClickListener {
      LSHTTPNetworkAccess.setCellularPermitted(!isOnlyWifi)

      profiles.profileUpdate { description ->
        description.copy(
          preferences = description.preferences.copy(downloadOnlyOnWIFI = !isOnlyWifi)
        )
      }
    }
  }

  private fun configureDebug() {
    this.settings2Debug.setOnClickListener {
      MainNavigation.Settings.openDebugSettings()
    }

    val profiles =
      Services
        .serviceDirectory()
        .requireService(ProfilesControllerType::class.java)

    // Show the debug settings menu, if enabled
    val visible = SettingsModel.showDebugSettings(profiles)
    if (visible) {
      this.settings2Debug.visibility = View.VISIBLE
    } else {
      this.settings2Debug.visibility = View.GONE
    }
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

  private fun configureNotifications() {
    this.configureNotificationsText()

    this.settings2NotificationsEnableDisable.setOnClickListener {
      try {
        val activity = this.requireActivity()
        if (!MainNotifications.notificationsArePermitted(activity)) {
          MainNotifications.requestPermissions(activity)
          this.view?.postDelayed(
            { this.configureNotificationsText() },
            5000L
          )
        } else {
          MainNotifications.requestDropPermissions(activity)
        }
      } catch (e: Throwable) {
        this.logger.debug("Failed to request permissions: ", e)
      }
    }
  }

  private fun configureNotificationsText() {
    try {
      val activity = this.requireActivity()
      if (MainNotifications.notificationsArePermitted(activity)) {
        this.settings2NotificationsEnableDisable.textSummary.text =
          activity.getString(R.string.settingsNotificationsEnabled)
      } else {
        this.settings2NotificationsEnableDisable.textSummary.text =
          activity.getString(R.string.settingsNotificationsDisabled)
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed to configure preference item: ", e)
    }
  }
}
