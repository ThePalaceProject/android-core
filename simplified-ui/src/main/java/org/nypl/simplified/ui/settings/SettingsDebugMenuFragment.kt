package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType

class SettingsDebugMenuFragment : Fragment(R.layout.debug), MainBackButtonConsumerType {

  private lateinit var toolbarBack: View
  private lateinit var itemBooks: ViewGroup
  private lateinit var itemStartup: ViewGroup
  private lateinit var itemRegistry: ViewGroup
  private lateinit var itemErrors: ViewGroup
  private lateinit var itemDRM: ViewGroup
  private lateinit var itemCustomOPDS: ViewGroup

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuFragment> {
    private class ScreenSettingsDebugMenu : ScreenDefinitionType<Unit, SettingsDebugMenuFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuFragment {
        return SettingsDebugMenuFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack = view.findViewById(R.id.debugToolbarBackIconTouch)
    this.toolbarBack.setOnClickListener {
      this.onBackButtonPressed()
    }

    this.itemBooks = view.findViewById(R.id.debugBooks)
    this.itemCustomOPDS = view.findViewById(R.id.debugCustomOPDS)
    this.itemDRM = view.findViewById(R.id.debugDrm)
    this.itemErrors = view.findViewById(R.id.debugErrors)
    this.itemRegistry = view.findViewById(R.id.debugRegistry)
    this.itemStartup = view.findViewById(R.id.debugStartup)

    this.itemBooks.setOnClickListener {
      MainNavigation.Settings.openBooks()
    }
    this.itemCustomOPDS.setOnClickListener {
      MainNavigation.Settings.openCustomOPDS()
    }
    this.itemDRM.setOnClickListener {
      MainNavigation.Settings.openDebugDRMSettings()
    }
    this.itemErrors.setOnClickListener {
      MainNavigation.Settings.openDebugErrorsSettings()
    }
    this.itemRegistry.setOnClickListener {
      MainNavigation.Settings.openDebugRegistrySettings()
    }
    this.itemStartup.setOnClickListener {
      MainNavigation.Settings.openDebugStartupSettings()
    }
  }

  override fun onStart() {
    super.onStart()
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }
}
