package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.thepalaceproject.mdm.MDMData
import org.thepalaceproject.mdm.MDMDataServiceType

class SettingsDebugMenuMDMFragment :
  Fragment(R.layout.debug_mdm),
  MainBackButtonConsumerType {
  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuMDMFragment> {
    private class ScreenSettingsDebugMenuMDM : ScreenDefinitionType<Unit, SettingsDebugMenuMDMFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() = Unit

      override fun fragment(): SettingsDebugMenuMDMFragment = SettingsDebugMenuMDMFragment()
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuMDMFragment> =
      ScreenSettingsDebugMenuMDM()
  }

  private lateinit var toolbarBack: View
  private lateinit var dataText: TextView
  private lateinit var errorsText: TextView

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack = view.findViewById(R.id.debugToolbarBackIconTouch)
    this.toolbarBack.setOnClickListener {
      this.onBackButtonPressed()
    }

    this.dataText =
      view.findViewById(R.id.debugMdmData)
    this.errorsText =
      view.findViewById(R.id.debugMdmErrors)
  }

  override fun onResume() {
    super.onResume()
    this.update()
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private fun update() {
    val dataService =
      Services
        .serviceDirectory()
        .optionalService(MDMDataServiceType::class.java)
    val data =
      dataService?.mostRecent()
    this.dataText.text =
      this.renderData(data)
    this.errorsText.text =
      this.renderErrors(data)
  }

  private fun renderData(data: MDMData?): String {
    val values =
      data?.rawValues
    if (values.isNullOrEmpty()) {
      return "No MDM data has been received"
    }
    return values.entries
      .sortedBy { it.key }
      .joinToString(separator = "\n") { (key, value) -> "$key = $value" }
  }

  private fun renderErrors(data: MDMData?): String {
    if (data == null) {
      return "No MDM data has been received"
    }
    val invalidFields =
      data.parseResult.invalidFields
    if (invalidFields.isEmpty()) {
      return "Data was parsed successfully"
    }
    return invalidFields
      .joinToString(separator = "\n") { field ->
        "${field.field}: \"${field.value}\" is not a valid urn:uuid"
      }
  }
}
