package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.taskrecorder.api.TaskStepResolution
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.nypl.simplified.viewer.api.Viewers

class SettingsDebugMenuErrorsFragment : Fragment(R.layout.debug_errors),
  MainBackButtonConsumerType {

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuErrorsFragment> {
    private class ScreenSettingsDebugMenu :
      ScreenDefinitionType<Unit, SettingsDebugMenuErrorsFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuErrorsFragment {
        return SettingsDebugMenuErrorsFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuErrorsFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private lateinit var makeNextBookFail: SwitchCompat
  private lateinit var sendAnalytics: Button
  private lateinit var showErrorPage: Button
  private lateinit var sendErrorLogs: Button
  private lateinit var crash: Button
  private lateinit var toolbarBack: View

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack = view.findViewById(R.id.debugToolbarBackIconTouch)
    this.toolbarBack.setOnClickListener {
      this.onBackButtonPressed()
    }

    this.sendErrorLogs =
      view.findViewById(R.id.debugSendErrorLogs)
    this.showErrorPage =
      view.findViewById(R.id.debugShowErrorPage)
    this.sendAnalytics =
      view.findViewById(R.id.debugSendAnalytics)
    this.crash =
      view.findViewById(R.id.debugCrash)

    this.sendErrorLogs.setOnClickListener {
      SettingsDebugModel.sendErrorLogs()
    }
    this.showErrorPage.setOnClickListener {
      this.showErrorPage()
    }
    this.sendAnalytics.setOnClickListener {
      SettingsDebugModel.sendAnalytics()
      Toast.makeText(
        this.requireContext(),
        "Triggered analytics send",
        Toast.LENGTH_SHORT
      ).show()
    }
    this.crash.setOnClickListener {
      throw DebugCrashedDeliberately()
    }

    this.makeNextBookFail =
      view.findViewById(R.id.debugErrorsMakeBookFail)
  }

  override fun onStart() {
    super.onStart()

    this.makeNextBookFail.isChecked = Viewers.shouldFailNextBook()
    this.makeNextBookFail.setOnCheckedChangeListener { _, isChecked ->
      Viewers.setFailNextBook(isChecked)
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
}
