package org.nypl.simplified.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.ui.R
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.slf4j.LoggerFactory

class SettingsMainFragment3 : PreferenceFragmentCompat() {

  private val logger =
    LoggerFactory.getLogger(SettingsMainFragment3::class.java)

  // XXX: Remove event based navigation when the catalog is rewritten
  private val settingsEventListener: FragmentListenerType<SettingsMainEvent>
    by this.fragmentListeners()

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

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(
      SettingsModel.profileEvents.subscribe(this::onProfileEvent)
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
      val context =
        this.requireContext()
      val pkgManager =
        context.packageManager
      val pkgInfo =
        pkgManager.getPackageInfo(context.packageName, 0)
      val versionName =
        SettingsModel.buildConfig.simplifiedVersion

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
    val doc = SettingsModel.documents.privacyPolicy
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(
            SettingsMainEvent.OpenPrivacy(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureLicense(
    preference: Preference
  ) {
    val doc = SettingsModel.documents.licenses
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(
            SettingsMainEvent.OpenLicense(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureFaq(
    preference: Preference
  ) {
    val doc = SettingsModel.documents.faq
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(
            SettingsMainEvent.OpenFAQ(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureEULA(
    preference: Preference
  ) {
    val doc = SettingsModel.documents.eula
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(
            SettingsMainEvent.OpenEULA(
              title = it.title.toString(),
              url = doc.readableURL.toString()
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
      this.settingsEventListener.post(SettingsMainEvent.OpenDebugOptions)
      true
    }

    // Show the debug settings menu, if enabled
    preference.isVisible = SettingsModel.showDebugSettings
  }

  private fun configureBuild(
    preference: Preference
  ) {
    preference.setSummaryProvider {
      SettingsModel.buildConfig.vcsCommit
    }
    preference.setOnPreferenceClickListener {
      SettingsModel.onClickVersion()
      true
    }
  }

  private fun configureAccounts(
    preference: Preference
  ) {
    if (SettingsModel.buildConfig.allowAccountsAccess) {
      preference.isEnabled = true
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(SettingsMainEvent.OpenAccountList)
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
    val doc = SettingsModel.documents.acknowledgements
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(
            SettingsMainEvent.OpenAcknowledgments(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureAbout(
    preference: Preference
  ) {
    val doc = SettingsModel.documents.about
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.settingsEventListener.post(
            SettingsMainEvent.OpenAbout(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }
}
