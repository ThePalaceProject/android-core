package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType

class SettingsDebugMenuStartupFragment : Fragment(R.layout.debug_startup),
  MainBackButtonConsumerType {

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuStartupFragment> {
    private class ScreenSettingsDebugMenu :
      ScreenDefinitionType<Unit, SettingsDebugMenuStartupFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuStartupFragment {
        return SettingsDebugMenuStartupFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuStartupFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private lateinit var hasSeenNotifications: SwitchCompat
  private lateinit var failNextBoot: SwitchCompat
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

    this.failNextBoot =
      view.findViewById(R.id.debugStartupFailNextBoot)
    this.failNextBoot.isChecked =
      SettingsDebugModel.isBootFailureEnabled
    this.failNextBoot.setOnCheckedChangeListener { _, checked ->
      SettingsDebugModel.isBootFailureEnabled = checked
    }

    this.hasSeenNotifications =
      view.findViewById(R.id.debugStartupSeenNotifications)
    this.hasSeenNotifications.isChecked =
      SettingsDebugModel.hasSeenNotificationScreen()
    this.hasSeenNotifications.setOnCheckedChangeListener { _, checked ->
      SettingsDebugModel.updatePreferences { p -> p.copy(hasSeenNotificationScreen = checked) }
    }
  }
}
