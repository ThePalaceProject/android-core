package org.nypl.simplified.ui.settings

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.documents.DocumentStoreType
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

    val profilePrefs = profiles.profileCurrent().preferences()
    this.configureAbout()
    this.configureAccounts()
    this.configureAudiobookSkip(profiles = profiles, profilePrefs = profilePrefs)
    this.configureBattery()
    this.configureBuild()
    this.configureDebug()
    this.configureEULA()
    this.configureLicense()
    this.configureNetwork(profiles = profiles, profilePrefs = profilePrefs)
    this.configureNotifications()
    this.configurePrivacy()
    this.configureVersion()
    BatteryModel.batteryOptimizerCheck()
  }

  private fun configureBuild() {
    try {
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
    } catch (e: Throwable) {
      this.logger.debug("configureBuild: ", e)
    }
  }

  private fun configureBattery() {
    try {
      this.settings2BatteryOptimizer.setOnClickListener {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        this.requireActivity().startActivity(intent)
      }
    } catch (e: Throwable) {
      this.logger.debug("configureBattery: ", e)
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
      this.configureAudiobookSkip(
        profiles = profiles,
        profilePrefs = profiles.profileCurrent().preferences()
      )
    }
  }

  private fun configureNetwork(
    profiles: ProfilesControllerType,
    profilePrefs: ProfilePreferences
  ) {
    try {
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
    } catch (e: Throwable) {
      this.logger.debug("configureNetwork: ", e)
    }
  }

  private fun configureDebug() {
    try {
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
    } catch (e: Throwable) {
      this.logger.debug("configureDebug: ", e)
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
    try {
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
    } catch (e: Throwable) {
      this.logger.debug("configureNotifications: ", e)
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

  private fun configureAudiobookSkip(
    profiles: ProfilesControllerType,
    profilePrefs: ProfilePreferences
  ) {
    try {
      val entries =
        this.resources.getStringArray(R.array.settingsAudiobooksSkipEntries)
      val values =
        this.resources.getStringArray(R.array.settingsAudiobooksSkipValues)

      this.configureAudiobookSkipItem(
        view = this.settings2AudiobooksSkipForward,
        currentValueMs = profilePrefs.audioBookPlaybackSkipIntervalForwardMs,
        entries = entries,
        values = values,
        onSelected = { newValue ->
          val seconds = newValue.toLong()
          val ms = TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS)
          profiles.profileUpdate { d ->
            d.copy(
              preferences =
                d.preferences.copy(
                  audioBookPlaybackSkipIntervalForwardMs = ms
                )
            )
          }
        }
      )

      this.configureAudiobookSkipItem(
        view = this.settings2AudiobooksSkipBackward,
        currentValueMs = profilePrefs.audioBookPlaybackSkipIntervalBackwardMs,
        entries = entries,
        values = values,
        onSelected = { newValue ->
          val seconds = newValue.toLong()
          val ms = TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS)
          profiles.profileUpdate { d ->
            d.copy(
              preferences =
                d.preferences.copy(
                  audioBookPlaybackSkipIntervalBackwardMs = ms
                )
            )
          }
        }
      )
    } catch (e: Throwable) {
      this.logger.debug("configureAudiobookSkip: ", e)
    }
  }

  private fun configureAudiobookSkipItem(
    view: SettingsTextView,
    currentValueMs: Long,
    entries: Array<String>,
    values: Array<String>,
    onSelected: (String) -> Unit
  ) {
    try {
      val currentValueSeconds =
        TimeUnit.SECONDS.convert(currentValueMs, TimeUnit.MILLISECONDS)
      val initialValue = currentValueSeconds.toString()

      val checkedIndex = values.indexOf(initialValue)
      if (checkedIndex >= 0) {
        view.textSummary.text = entries[checkedIndex]
      }

      view.setOnClickListener {
        SettingsListPreferenceDialog.show(
          fragment = this,
          title = view.textTitle.text,
          entries = entries,
          values = values,
          initialValue = initialValue,
          onSelected = { newValue ->
            val newIndex = values.indexOf(newValue)
            if (newIndex >= 0) {
              view.textSummary.text = entries[newIndex]
            }
            onSelected(newValue)
          }
        )
      }
    } catch (e: Throwable) {
      this.logger.debug("configureAudiobookSkipItem: ", e)
    }
  }

  private fun configureAccounts() {
    try {
      val services =
        Services.serviceDirectory()
      val buildConfig =
        services.requireService(BuildConfigurationServiceType::class.java)

      if (buildConfig.allowAccountsAccess) {
        this.settings2AddRemoveAccounts.setOnClickListener {
          MainNavigation.Settings.openAccountList()
        }
      } else {
        this.settings2AddRemoveAccounts.visibility = View.GONE
      }
    } catch (e: Throwable) {
      this.logger.debug("configureAccounts: ", e)
    }
  }

  private fun configureAbout() {
    try {
      val services =
        Services.serviceDirectory()
      val documents =
        services.requireService(DocumentStoreType::class.java)

      val doc = documents.about
      if (doc != null) {
        this.settings2AboutPalace.setOnClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title =
                this.settings2AboutPalace.textTitle.text
                  .toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
        }
      } else {
        this.settings2AboutPalace.visibility = View.GONE
      }
    } catch (e: Throwable) {
      this.logger.debug("configureAbout: ", e)
    }
  }

  private fun configureEULA() {
    try {
      val services =
        Services.serviceDirectory()
      val documents =
        services.requireService(DocumentStoreType::class.java)

      val doc = documents.eula
      if (doc != null) {
        this.settings2UserAgreement.setOnClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title =
                this.settings2UserAgreement.textTitle.text
                  .toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
        }
      } else {
        this.settings2UserAgreement.visibility = View.GONE
      }
    } catch (e: Throwable) {
      this.logger.debug("configureEULA: ", e)
    }
  }

  private fun configureLicense() {
    try {
      val services =
        Services.serviceDirectory()
      val documents =
        services.requireService(DocumentStoreType::class.java)

      val doc = documents.licenses
      if (doc != null) {
        this.settings2Licenses.setOnClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title =
                this.settings2Licenses.textTitle.text
                  .toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
        }
      } else {
        this.settings2Licenses.visibility = View.GONE
      }
    } catch (e: Throwable) {
      this.logger.debug("configureLicense: ", e)
    }
  }

  private fun configurePrivacy() {
    try {
      val services =
        Services.serviceDirectory()
      val documents =
        services.requireService(DocumentStoreType::class.java)

      val doc = documents.privacyPolicy
      if (doc != null) {
        this.settings2PrivacyPolicy.setOnClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title =
                this.settings2PrivacyPolicy.textTitle.text
                  .toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
        }
      } else {
        this.settings2PrivacyPolicy.visibility = View.GONE
      }
    } catch (e: Throwable) {
      this.logger.debug("configurePrivacy: ", e)
    }
  }

  private fun formatVersion(): String =
    try {
      val services =
        Services.serviceDirectory()
      val buildConfig =
        services.requireService(BuildConfigurationServiceType::class.java)

      val context = this.requireContext()
      val pkgManager = context.packageManager
      val pkgInfo = pkgManager.getPackageInfo(context.packageName, 0)
      val versionName = buildConfig.simplifiedVersion

      "$versionName (${pkgInfo.versionCode})"
    } catch (e: Throwable) {
      "Unknown"
    }

  private fun configureVersion() {
    try {
      this.settings2AppVersion.textSummary.text = this.formatVersion()
    } catch (e: Throwable) {
      this.logger.debug("configureVersion: ", e)
    }
  }
}
