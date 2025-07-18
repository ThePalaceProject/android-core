package org.nypl.simplified.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.TextView.BufferType.EDITABLE
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryDebugging
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.taskrecorder.api.TaskStepResolution
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory

/**
 * A fragment that shows various debug options for testing app functionality at runtime.
 */

class SettingsDebugFragment : Fragment(R.layout.settings_debug), MainBackButtonConsumerType {

  private val LIBRARY_REGISTRY_DEBUG_PROPERTY =
    "org.nypl.simplified.accounts.source.nyplregistry.baseServerOverride"
  private val logger =
    LoggerFactory.getLogger(SettingsDebugFragment::class.java)

  private lateinit var accountProviders: AccountProviderRegistryType
  private lateinit var adobeDRMActivationTable: TableLayout
  private lateinit var cacheButton: Button
  private lateinit var crashButton: Button
  private lateinit var crashlyticsId: TextView
  private lateinit var customOPDS: Button
  private lateinit var drmTable: TableLayout
  private lateinit var failNextBoot: SwitchCompat
  private lateinit var forgetAnnouncementsButton: Button
  private lateinit var hasSeenLibrarySelection: SwitchCompat
  private lateinit var isManualLCPPassphraseEnabled: SwitchCompat
  private lateinit var libraryRegistryClear: Button
  private lateinit var libraryRegistryEntry: EditText
  private lateinit var libraryRegistrySet: Button
  private lateinit var sendAnalyticsButton: Button
  private lateinit var sendReportButton: Button
  private lateinit var showErrorButton: Button
  private lateinit var showOnlySupportedBooks: SwitchCompat
  private lateinit var showTesting: SwitchCompat
  private lateinit var syncAccountsButton: Button
  private lateinit var toolbarBack: View

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugFragment> {
    private class ScreenSettingsDebug :
      ScreenDefinitionType<Unit, SettingsDebugFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugFragment {
        return SettingsDebugFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugFragment> {
      return ScreenSettingsDebug()
    }
  }

  /**
   * Subscriptions that will be closed when the fragment is detached (ie. when the app is backgrounded).
   */

  private var subscriptions =
    CloseableCollection.create()

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack =
      view.findViewById(R.id.settingsDebugToolbarBackIconTouch)
    this.crashButton =
      view.findViewById(R.id.settingsVersionDevCrash)
    this.cacheButton =
      view.findViewById(R.id.settingsVersionDevShowCacheDir)
    this.sendReportButton =
      view.findViewById(R.id.settingsVersionDevSendReports)
    this.showErrorButton =
      view.findViewById(R.id.settingsVersionDevShowError)
    this.sendAnalyticsButton =
      view.findViewById(R.id.settingsVersionDevSyncAnalytics)
    this.syncAccountsButton =
      view.findViewById(R.id.settingsVersionDevSyncAccounts)
    this.forgetAnnouncementsButton =
      view.findViewById(R.id.settingsVersionDevUnacknowledgeAnnouncements)
    this.drmTable =
      view.findViewById(R.id.settingsVersionDrmSupport)
    this.adobeDRMActivationTable =
      view.findViewById(R.id.settingsVersionDrmAdobeActivations)
    this.showTesting =
      view.findViewById(R.id.settingsVersionDevProductionLibrariesSwitch)
    this.failNextBoot =
      view.findViewById(R.id.settingsVersionDevFailNextBootSwitch)
    this.hasSeenLibrarySelection =
      view.findViewById(R.id.settingsVersionDevSeenLibrarySelectionScreen)
    this.isManualLCPPassphraseEnabled =
      view.findViewById(R.id.settingsVersionDevIsManualLCPPassphraseEnabled)
    this.showOnlySupportedBooks =
      view.findViewById(R.id.settingsVersionDevShowOnlySupported)
    this.customOPDS =
      view.findViewById(R.id.settingsVersionDevCustomOPDS)
    this.crashlyticsId =
      view.findViewById(R.id.settingsVersionCrashlyticsID)
    this.libraryRegistryClear =
      view.findViewById(R.id.libraryRegistryOverrideClear)
    this.libraryRegistryEntry =
      view.findViewById(R.id.libraryRegistryOverrideBase)
    this.libraryRegistrySet =
      view.findViewById(R.id.libraryRegistryOverrideSet)

    this.drmTable.addView(
      this.createDrmSupportRow("Adobe Acs", SettingsDebugModel.adeptSupported())
    )
    this.drmTable.addView(
      this.createDrmSupportRow("Boundless", SettingsDebugModel.boundlessSupported())
    )

    this.showTesting.isChecked =
      SettingsDebugModel.showTestingLibraries()
    this.failNextBoot.isChecked =
      SettingsDebugModel.isBootFailureEnabled
    this.hasSeenLibrarySelection.isChecked =
      SettingsDebugModel.hasSeenLibrarySelection()
    this.isManualLCPPassphraseEnabled.isChecked =
      SettingsDebugModel.isManualLCPPassphraseEnabled()
    this.showOnlySupportedBooks.isChecked =
      SettingsDebugModel.showOnlySupportedBooks()
    this.crashlyticsId.text =
      SettingsDebugModel.crashlyticsId()
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()

    this.accountProviders =
      Services.serviceDirectory()
        .requireService(AccountProviderRegistryType::class.java)

    this.toolbarBack.setOnClickListener {
      this.toolbarBack.postDelayed(MainNavigation.Settings::goUp, 500)
    }

    this.crashButton.setOnClickListener {
      throw OutOfMemoryError("Pretending to have run out of memory!")
    }

    this.cacheButton.setOnClickListener {
      this.showCacheAlert()
    }

    this.sendReportButton.setOnClickListener {
      SettingsDebugModel.sendErrorLogs()
    }

    this.showErrorButton.setOnClickListener {
      this.showErrorPage()
    }

    /*
     * A button that publishes a "sync requested" event to the analytics system. Registered
     * analytics implementations are expected to respond to this event and publish any buffered
     * data that they may have to remote servers.
     */

    this.sendAnalyticsButton.setOnClickListener {
      SettingsDebugModel.sendAnalytics()
      Toast.makeText(
        this.requireContext(),
        "Triggered analytics send",
        Toast.LENGTH_SHORT
      ).show()
    }

    this.syncAccountsButton.setOnClickListener {
      SettingsDebugModel.syncAccounts()
      Toast.makeText(
        this.requireContext(),
        "Triggered sync of all accounts",
        Toast.LENGTH_SHORT
      ).show()
    }

    /*
     * Forget announcements when the button is clicked.
     */

    this.forgetAnnouncementsButton.setOnClickListener {
      SettingsDebugModel.forgetAllAnnouncements()
    }

    /*
     * Update the current profile's preferences whenever the testing switch is changed.
     */

    this.showTesting.setOnCheckedChangeListener { _, checked ->
      SettingsDebugModel.updatePreferences { p -> p.copy(showTestingLibraries = checked) }
      this.accountProviders.refreshAsync(
        includeTestingLibraries = checked,
        useCache = false
      )
    }

    /*
     * Configure the "fail next boot" switch to enable/disable boot failures.
     */

    this.failNextBoot.setOnCheckedChangeListener { _, checked ->
      SettingsDebugModel.isBootFailureEnabled = checked
    }

    /*
     * Configure the "has seen library selection" switch
     */

    this.hasSeenLibrarySelection.setOnCheckedChangeListener { _, checked ->
      SettingsDebugModel.updatePreferences { p -> p.copy(hasSeenLibrarySelectionScreen = checked) }
    }

    /*
     * Update the feed loader when filtering options are changed.
     */

    this.showOnlySupportedBooks.setOnCheckedChangeListener { _, isChecked ->
      SettingsDebugModel.setShowOnlySupportedBooks(showOnlySupported = isChecked)
    }
    this.isManualLCPPassphraseEnabled.setOnCheckedChangeListener { _, isChecked ->
      SettingsDebugModel.updatePreferences { p -> p.copy(isManualLCPPassphraseEnabled = isChecked) }
    }

    /*
     * Configure the custom OPDS button.
     */

    this.customOPDS.setOnClickListener {
      MainNavigation.Settings.openCustomOPDS()
    }

    this.configureLibraryRegistryCustomUI()

    this.subscriptions.add(
      SettingsDebugModel.adeptActivations.subscribe { _, activations ->
        this.onAdobeDRMReceivedActivations(activations)
      }
    )

    SettingsDebugModel.fetchAdobeActivations()
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }

  /**
   * Configure the UI for setting custom library registry servers.
   */

  private fun configureLibraryRegistryCustomUI() {
    this.libraryRegistryClear.setOnClickListener {
      AccountProviderRegistryDebugging.clearProperty(LIBRARY_REGISTRY_DEBUG_PROPERTY)
      this.libraryRegistryEntry.setText("", EDITABLE)
    }

    this.libraryRegistrySet.setOnClickListener {
      val target = this.libraryRegistryEntry.text.toString()
      this.logger.debug("set custom library registry server: {}", target)

      AccountProviderRegistryDebugging.setProperty(
        name = LIBRARY_REGISTRY_DEBUG_PROPERTY,
        value = target
      )

      Services.serviceDirectory()
        .requireService(AccountProviderRegistryType::class.java)
        .clear()

      Toast.makeText(
        this.requireContext(),
        "Set custom library registry base: '$target' and cleared cache",
        LENGTH_LONG
      ).show()
    }

    val customServer = AccountProviderRegistryDebugging.property(LIBRARY_REGISTRY_DEBUG_PROPERTY)
    if (customServer != null) {
      this.libraryRegistryEntry.setText(customServer, EDITABLE)
    }
  }

  private fun showErrorPage() {
    val appVersion =
      SettingsDebugModel.appVersion()
    val attributes = sortedMapOf(
      Pair("Version", appVersion)
    )

    val taskSteps =
      mutableListOf<TaskStep>()

    taskSteps.add(
      TaskStep(
        "Opening error page.",
        TaskStepResolution.TaskStepSucceeded("Error page successfully opened.")
      )
    )

    val supportEmail =
      Services.serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)
        .supportErrorReportEmailAddress

    MainNavigation.openErrorPage(
      activity = this.requireActivity(),
      parameters = ErrorPageParameters(
        emailAddress = supportEmail,
        body = "",
        subject = "[palace-error-report] $appVersion",
        attributes = attributes,
        taskSteps = taskSteps
      )
    )
  }

  private fun showCacheAlert() {
    val context = this.requireContext()
    val message = StringBuilder(128)
    message.append("Cache directory is: ")
    message.append(context.cacheDir)
    message.append("\n")
    message.append("\n")
    message.append("Exists: ")
    message.append(context.cacheDir?.isDirectory ?: false)
    message.append("\n")

    MaterialAlertDialogBuilder(context)
      .setTitle("Cache Directory")
      .setMessage(message.toString())
      .show()
  }

  private fun createDrmSupportRow(name: String, isSupported: Boolean): TableRow {
    val row =
      this.layoutInflater.inflate(
        R.layout.settings_version_table_item, this.drmTable, false
      ) as TableRow
    val key =
      row.findViewById<TextView>(R.id.key)
    val value =
      row.findViewById<TextView>(R.id.value)

    key.text = name

    if (isSupported) {
      value.setTextColor(Color.GREEN)
      value.text = "Supported"
    } else {
      value.setTextColor(Color.RED)
      value.text = "Unsupported"
    }

    return row
  }

  private fun onAdobeDRMReceivedActivations(
    activations: List<AdobeDRMExtensions.Activation>
  ) {
    this.adobeDRMActivationTable.removeAllViews()

    this.run {
      val row =
        this.layoutInflater.inflate(
          R.layout.settings_drm_activation_table_item,
          this.adobeDRMActivationTable,
          false
        ) as TableRow
      val index = row.findViewById<TextView>(R.id.index)
      val vendor = row.findViewById<TextView>(R.id.vendor)
      val device = row.findViewById<TextView>(R.id.device)
      val userName = row.findViewById<TextView>(R.id.userName)
      val userId = row.findViewById<TextView>(R.id.userId)
      val expiry = row.findViewById<TextView>(R.id.expiry)

      index.text = "Index"
      vendor.text = "Vendor"
      device.text = "Device"
      userName.text = "UserName"
      userId.text = "UserID"
      expiry.text = "Expiry"

      this.adobeDRMActivationTable.addView(row)
    }

    for (activation in activations) {
      val row =
        this.layoutInflater.inflate(
          R.layout.settings_drm_activation_table_item,
          this.adobeDRMActivationTable,
          false
        ) as TableRow
      val index = row.findViewById<TextView>(R.id.index)
      val vendor = row.findViewById<TextView>(R.id.vendor)
      val device = row.findViewById<TextView>(R.id.device)
      val userName = row.findViewById<TextView>(R.id.userName)
      val userId = row.findViewById<TextView>(R.id.userId)
      val expiry = row.findViewById<TextView>(R.id.expiry)

      index.text = activation.index.toString()
      vendor.text = activation.vendor.value
      device.text = activation.device.value
      userName.text = activation.userName
      userId.text = activation.userID.value
      expiry.text = activation.expiry ?: "No expiry"

      this.adobeDRMActivationTable.addView(row)
    }
  }

  override fun onBackButtonPressed(): Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }
}
