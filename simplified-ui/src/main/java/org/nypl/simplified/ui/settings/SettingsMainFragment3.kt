package org.nypl.simplified.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory

class SettingsMainFragment3 : PreferenceFragmentCompat() {

  private val logger =
    LoggerFactory.getLogger(SettingsMainFragment3::class.java)

  private lateinit var settingsAbout: Preference
  private lateinit var settingsAccounts: Preference
  private lateinit var settingsAcknowledgements: Preference
  private lateinit var settingsCommit: Preference
  private lateinit var settingsDebug: Preference
  private lateinit var settingsEULA: Preference
  private lateinit var settingsFaq: Preference
  private lateinit var settingsLicense: Preference
  private lateinit var settingsPrivacy: Preference
  private lateinit var settingsVersion: Preference
  private lateinit var settingsVersionCore: Preference

  private var subscriptions = CompositeDisposable()

  companion object : ScreenDefinitionFactoryType<Unit, SettingsMainFragment3> {
    private class ScreenSettingsMain : ScreenDefinitionType<Unit, SettingsMainFragment3> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsMainFragment3 {
        return SettingsMainFragment3()
      }
    }

    override fun createScreenDefinition(
      p: Unit
    ): ScreenDefinitionType<Unit, SettingsMainFragment3> {
      return ScreenSettingsMain()
    }
  }

  override fun onStart() {
    super.onStart()

    val services =
      Services.serviceDirectory()
    val profileEvents =
      services.requireService(SettingsProfileEvents::class.java)

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(
      profileEvents.events.subscribe(this::onProfileEvent)
    )

    try {
      this.configureDebug(this.settingsDebug)
    } catch (e: Throwable) {
      this.logger.debug("Error configuring debug menu: ", e)
    }
  }

  private fun onProfileEvent(e: ProfileEvent) {
    if (e is ProfileUpdated) {
      this.configureDebug(this.settingsDebug)
    }
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.dispose()
  }

  override fun onCreatePreferences(
    savedInstanceState: Bundle?,
    rootKey: String?
  ) {
    this.setPreferencesFromResource(R.xml.settings, rootKey)

    this.settingsAbout = this.findPreference("settingsAbout")!!
    this.settingsAcknowledgements = this.findPreference("settingsAcknowledgements")!!
    this.settingsAccounts = this.findPreference("settingsAccounts")!!
    this.settingsCommit = this.findPreference("settingsCommit")!!
    this.settingsDebug = this.findPreference("settingsDebug")!!
    this.settingsEULA = this.findPreference("settingsEULA")!!
    this.settingsFaq = this.findPreference("settingsFaq")!!
    this.settingsLicense = this.findPreference("settingsLicense")!!
    this.settingsPrivacy = this.findPreference("settingsPrivacy")!!
    this.settingsVersion = this.findPreference("settingsVersion")!!
    this.settingsVersionCore = this.findPreference("settingsVersionCore")!!

    this.configureAbout(this.settingsAbout)
    this.configureAcknowledgements(this.settingsAcknowledgements)
    this.configureAccounts(this.settingsAccounts)
    this.configureBuild(this.settingsCommit)
    this.configureDebug(this.settingsDebug)
    this.configureEULA(this.settingsEULA)
    this.configureFaq(this.settingsFaq)
    this.configureLicense(this.settingsLicense)
    this.configurePrivacy(this.settingsPrivacy)
    this.configureVersion(this.settingsVersion)
    this.configureVersionCore(this.settingsVersionCore)
  }

  private fun formatVersion(): String {
    return try {
      val services =
        Services.serviceDirectory()
      val buildConfig =
        services.requireService(BuildConfigurationServiceType::class.java)

      val context =
        this.requireContext()
      val pkgManager =
        context.packageManager
      val pkgInfo =
        pkgManager.getPackageInfo(context.packageName, 0)
      val versionName =
        buildConfig.simplifiedVersion

      "$versionName (${pkgInfo.versionCode})"
    } catch (e: Throwable) {
      "Unknown"
    }
  }

  private fun configureVersion(
    preference: Preference
  ) {
    preference.setSummaryProvider { this.formatVersion() }
  }

  private fun configureVersionCore(
    preference: Preference
  ) {
    // Hide the Core version if it's similar to the app version
    preference.isVisible = false
  }

  private fun configurePrivacy(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val documents =
      services.requireService(DocumentStoreType::class.java)

    val doc = documents.privacyPolicy
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title = it.title.toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
          true
        }
    }
  }

  private fun configureLicense(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val documents =
      services.requireService(DocumentStoreType::class.java)

    val doc = documents.licenses
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title = it.title.toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
          true
        }
    }
  }

  private fun configureFaq(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val documents =
      services.requireService(DocumentStoreType::class.java)

    val doc = documents.faq
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title = it.title.toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
          true
        }
    }
  }

  private fun configureEULA(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val documents =
      services.requireService(DocumentStoreType::class.java)

    val doc = documents.eula
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title = it.title.toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
          true
        }
    }
  }

  private fun configureDebug(
    preference: Preference
  ) {
    preference.setOnPreferenceClickListener {
      MainNavigation.Settings.openDebugSettings()
      true
    }

    val profiles =
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)

    // Show the debug settings menu, if enabled
    preference.isVisible = SettingsModel.showDebugSettings(profiles)
  }

  private fun configureBuild(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    preference.setSummaryProvider {
      buildConfig.vcsCommit
    }
    preference.setOnPreferenceClickListener {
      SettingsModel.onClickVersion(profiles)
      true
    }
  }

  private fun configureAccounts(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    if (buildConfig.allowAccountsAccess) {
      preference.isEnabled = true
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openAccountList()
          true
        }
    } else {
      preference.isVisible = false
      preference.isEnabled = false
    }
  }

  private fun configureAcknowledgements(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val documents =
      services.requireService(DocumentStoreType::class.java)

    val doc = documents.acknowledgements
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title = it.title.toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
          true
        }
    }
  }

  private fun configureAbout(
    preference: Preference
  ) {
    val services =
      Services.serviceDirectory()
    val documents =
      services.requireService(DocumentStoreType::class.java)

    val doc = documents.about
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          MainNavigation.Settings.openDocument(
            SettingsDocumentViewerModel.DocumentTarget(
              title = it.title.toString(),
              url = doc.readableURL.toExternalForm()
            )
          )
          true
        }
    }
  }
}
