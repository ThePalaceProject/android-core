package org.nypl.simplified.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType

class SettingsDebugMenuAutoFragment :
  Fragment(R.layout.debug_auto),
  MainBackButtonConsumerType {
  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuAutoFragment> {
    private class ScreenSettingsDebugMenu : ScreenDefinitionType<Unit, SettingsDebugMenuAutoFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() = Unit

      override fun fragment(): SettingsDebugMenuAutoFragment = SettingsDebugMenuAutoFragment()
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuAutoFragment> = ScreenSettingsDebugMenu()
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private lateinit var enabled: TextView
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

    this.enabled =
      view.findViewById(R.id.debugAutoEnabled)
  }

  override fun onStart() {
    super.onStart()

    if (hasCarApplicationMetadata(this.requireContext())) {
      this.enabled.text = "In this build, Android Auto is ENABLED."
    } else {
      this.enabled.text = "In this build, Android Auto is DISABLED."
    }
  }

  private fun hasCarApplicationMetadata(context: Context): Boolean {
    val appInfo =
      context.packageManager.getApplicationInfo(
        context.packageName,
        PackageManager.GET_META_DATA
      )

    return appInfo.metaData?.containsKey(
      "com.google.android.gms.car.application"
    ) == true
  }
}
