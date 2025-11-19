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

class SettingsDebugMenuBooksFragment : Fragment(R.layout.debug_books),
  MainBackButtonConsumerType {

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuBooksFragment> {
    private class ScreenSettingsDebugMenu :
      ScreenDefinitionType<Unit, SettingsDebugMenuBooksFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuBooksFragment {
        return SettingsDebugMenuBooksFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuBooksFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private lateinit var showOnlySupportedBooks: SwitchCompat
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

    this.showOnlySupportedBooks =
      view.findViewById(R.id.debugBooksShowOnlySupported)
  }

  override fun onStart() {
    super.onStart()

    this.showOnlySupportedBooks.isChecked =
      SettingsDebugModel.showOnlySupportedBooks()
    this.showOnlySupportedBooks.setOnCheckedChangeListener { _, isChecked ->
      SettingsDebugModel.setShowOnlySupportedBooks(showOnlySupported = isChecked)
    }
  }
}
