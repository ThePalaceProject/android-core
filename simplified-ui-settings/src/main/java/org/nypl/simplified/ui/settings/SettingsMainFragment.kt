package org.nypl.simplified.ui.settings

import org.librarysimplified.ui.settings.R

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.librarysimplified.services.api.Services
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.slf4j.LoggerFactory
import org.thepalaceproject.theme.core.PalaceToolbar

/**
 * The main settings page containing links to other settings pages.
 */

class SettingsMainFragment : PreferenceFragmentCompat() {

  private val logger = LoggerFactory.getLogger(SettingsMainFragment::class.java)
  private val viewModel: SettingsMainViewModel by viewModels()
  private val listener: FragmentListenerType<SettingsMainEvent> by fragmentListeners()

  private val services =
    Services.serviceDirectory()
  private val configurationService =
    services.requireService(BuildConfigurationServiceType::class.java)

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
  private lateinit var toolbar: PalaceToolbar

  private var toast: Toast? = null
  private var tapToDebugSettings = 7

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

  override fun onStart() {
    super.onStart()
    this.configureToolbar()
  }

  private fun configureToolbar() {
    val actionBar = this.supportActionBar ?: return
    actionBar.setLogo(this.configurationService.brandingAppIcon)
    actionBar.setHomeActionContentDescription(R.string.settings)
    actionBar.setTitle(R.string.settings)
    this.toolbar.setLogoOnClickListener {
      // Do nothing.
    }
  }

  private fun configureAcknowledgements(preference: Preference) {
    val doc = this.viewModel.documents.acknowledgements
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(
            SettingsMainEvent.OpenAcknowledgments(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)
  }

  private fun configureVersion(preference: Preference) {
    preference.setSummaryProvider { this.viewModel.appVersion }
  }

  private fun configureVersionCore(preference: Preference) {
    preference.setSummaryProvider { this.viewModel.buildConfig.simplifiedVersion }

    // Hide the Core version if it's similar to the app version
    preference.isVisible =
      !this.viewModel.appVersion.startsWith(this.viewModel.buildConfig.simplifiedVersion)
  }

  private fun configureBuild(preference: Preference) {
    preference.setSummaryProvider { this.viewModel.buildConfig.vcsCommit }

    if (!this.viewModel.showDebugSettings) {
      preference.setOnPreferenceClickListener {
        this.onTapToDebugSettings(it)
        true
      }
    }
  }

  private fun configureDebug(preference: Preference) {
    preference.setOnPreferenceClickListener {
      this.listener.post(SettingsMainEvent.OpenDebugOptions)
      true
    }

    // Show the debug settings menu, if enabled
    preference.isVisible = this.viewModel.showDebugSettings
  }

  private fun configureLicense(preference: Preference) {
    val doc = this.viewModel.documents.licenses
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(
            SettingsMainEvent.OpenLicense(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureFaq(preference: Preference) {
    val doc = this.viewModel.documents.faq
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(
            SettingsMainEvent.OpenFAQ(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureEULA(preference: Preference) {
    val doc = this.viewModel.documents.eula
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(
            SettingsMainEvent.OpenEULA(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configurePrivacy(preference: Preference) {
    val doc = this.viewModel.documents.privacyPolicy
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(
            SettingsMainEvent.OpenPrivacy(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun configureAccounts(preference: Preference) {
    if (this.viewModel.buildConfig.allowAccountsAccess) {
      preference.isEnabled = true
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(SettingsMainEvent.OpenAccountList)
          true
        }
    } else {
      preference.isVisible = false
      preference.isEnabled = false
    }
  }

  private fun configureAbout(preference: Preference) {
    val doc = this.viewModel.documents.about
    preference.isVisible = doc != null
    if (doc != null) {
      preference.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          this.listener.post(
            SettingsMainEvent.OpenAbout(
              title = it.title.toString(),
              url = doc.readableURL.toString()
            )
          )
          true
        }
    }
  }

  private fun onTapToDebugSettings(preference: Preference) {
    val context = this.context ?: return

    if (this.tapToDebugSettings == 0) {
      this.viewModel.showDebugSettings = true
      this.settingsDebug.isVisible = true

      // Cancel the toast
      this.toast?.cancel()

      // Reveal the preference, if hidden
      this.listView.run {
        smoothScrollToPosition(adapter!!.itemCount)
      }

      // Unset our click listener
      preference.onPreferenceClickListener = null
    } else {
      if (this.tapToDebugSettings < 6) {
        val message =
          context.getString(R.string.settingsTapToDebug, this.tapToDebugSettings)

        this.toast?.cancel()
        this.toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        this.toast?.show()
      }
      this.tapToDebugSettings -= 1
    }
  }
}
