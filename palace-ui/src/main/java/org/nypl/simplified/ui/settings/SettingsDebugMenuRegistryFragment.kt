package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.TextView.BufferType.EDITABLE
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.annotation.UiThread
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryDebugging
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryRefresh
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory

class SettingsDebugMenuRegistryFragment : Fragment(R.layout.debug_registry),
  MainBackButtonConsumerType {

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuRegistryFragment> {
    private class ScreenSettingsDebugMenu :
      ScreenDefinitionType<Unit, SettingsDebugMenuRegistryFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuRegistryFragment {
        return SettingsDebugMenuRegistryFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuRegistryFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private val logger =
    LoggerFactory.getLogger(SettingsDebugMenuRegistryFragment::class.java)
  private val LIBRARY_REGISTRY_DEBUG_PROPERTY =
    "org.nypl.simplified.accounts.source.nyplregistry.baseServerOverride"

  private lateinit var error: ImageView
  private lateinit var debugRegistryQALibraries: SwitchCompat
  private lateinit var libraryRegistryClear: Button
  private lateinit var libraryRegistryEntry: EditText
  private lateinit var libraryRegistrySet: Button
  private lateinit var registryRefreshFull: Button
  private lateinit var registryRefreshIncremental: Button
  private lateinit var registryStatus: TextView
  private lateinit var registryStatusProgress: ProgressBar
  private lateinit var toolbarBack: View

  private var subscriptions: CloseableCollectionType<*> =
    CloseableCollection.create()

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack = view.findViewById(R.id.debugToolbarBackIconTouch)
    this.toolbarBack.setOnClickListener {
      this.onBackButtonPressed()
    }

    this.registryStatus =
      view.findViewById(R.id.debugRegistryStatus)
    this.registryStatusProgress =
      view.findViewById(R.id.debugRegistryStatusLoading)
    this.registryRefreshFull =
      view.findViewById(R.id.debugRegistryRefreshFull)
    this.registryRefreshIncremental =
      view.findViewById(R.id.debugRegistryRefreshIncremental)

    this.libraryRegistryClear =
      view.findViewById(R.id.libraryRegistryOverrideClear)
    this.libraryRegistryEntry =
      view.findViewById(R.id.libraryRegistryOverrideBase)
    this.libraryRegistrySet =
      view.findViewById(R.id.libraryRegistryOverrideSet)

    this.debugRegistryQALibraries =
      view.findViewById(R.id.debugRegistryQALibraries)
    this.debugRegistryQALibraries.isChecked =
      SettingsDebugModel.showTestingLibraries()

    this.error =
      view.findViewById(R.id.debugRegistryError)

    this.configureLibraryRegistryCustomUI()
  }

  override fun onStart() {
    super.onStart()
    this.subscriptions = CloseableCollection.create()

    val services =
      Services.serviceDirectory()
    val registry =
      services.requireService(AccountProviderRegistryType::class.java)

    this.subscriptions.add(
      registry.statusAttribute.subscribe { _, newValue ->
        UIThread.runOnUIThread {
          try {
            this.updateRegistryStatus(newValue, registry)
          } catch (e: Throwable) {
            // Don't care.
          }
        }
      }
    )

    this.registryRefreshFull.setOnClickListener {
      registry.refreshAsync(
        AccountProviderRegistryRefresh.Full(
          clearBeforeRefresh = true,
          includeTestingLibraries = SettingsDebugModel.showTestingLibraries(),
        )
      )
    }
    this.registryRefreshIncremental.setOnClickListener {
      registry.refreshAsync(
        AccountProviderRegistryRefresh.Incremental(
          includeTestingLibraries = SettingsDebugModel.showTestingLibraries(),
        )
      )
    }
    this.debugRegistryQALibraries.setOnCheckedChangeListener { _, checked ->
      SettingsDebugModel.updatePreferences { p -> p.copy(showTestingLibraries = checked) }
      registry.refreshAsync(
        AccountProviderRegistryRefresh.Full(
          clearBeforeRefresh = true,
          includeTestingLibraries = checked,
        )
      )
    }
  }

  @UiThread
  private fun updateRegistryStatus(
    status: AccountProviderRegistryStatus,
    registry: AccountProviderRegistryType
  ) {
    val descriptions =
      registry.accountProviderDescriptions()
    val resolved =
      registry.resolvedProviders

    val text = StringBuilder()
    when (status) {
      is AccountProviderRegistryStatus.Idle -> {
        this.error.visibility = View.GONE
        this.registryStatusProgress.isIndeterminate = false
        this.registryStatusProgress.setProgress(100, false)
        text.append("The registry is currently idle.\n")
        text.append("The registry contains ${descriptions.size} account providers.\n")
        text.append("${resolved.size} of the account providers have been resolved.\n")
        text.append("The last update modified ${status.lastUpdateAffected} providers.\n")
      }

      is AccountProviderRegistryStatus.Refreshing -> {
        this.error.visibility = View.GONE
        val progressPercent = status.progressPercent
        if (progressPercent == null) {
          this.registryStatusProgress.isIndeterminate = true
          text.append("The registry is currently refreshing (${status.kind}) ⌛\n")
        } else {
          this.registryStatusProgress.isIndeterminate = false
          this.registryStatusProgress.progress = progressPercent.toInt()
          text.append("The registry is currently refreshing ${progressPercent.toInt()}% (${status.kind}) ⌛\n")
        }
      }

      is AccountProviderRegistryStatus.Failed -> {
        this.error.visibility = View.VISIBLE
        this.error.setOnClickListener { this.openErrorPage(status.result) }
        this.registryStatusProgress.isIndeterminate = false
        this.registryStatusProgress.setProgress(100, false)
        text.append("The registry failed to update.\n")
        text.append(status.result.message)
      }
    }

    this.registryStatus.text = text.toString()
  }

  private fun openErrorPage(
    result: TaskResult<*>
  ) {
    val appVersion =
      SettingsDebugModel.appVersion()

    val supportEmail =
      Services.serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)
        .supportErrorReportEmailAddress

    MainNavigation.openErrorPage(
      activity = this.requireActivity(),
      parameters = ErrorPageParameters(
        emailAddress = supportEmail,
        body = result.message,
        subject = "[palace-error-report] $appVersion",
        attributes = result.attributes.toSortedMap(),
        taskSteps = result.steps
      )
    )
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
}
