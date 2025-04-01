package org.nypl.simplified.ui.main

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountCardCreatorModel
import org.nypl.simplified.ui.accounts.AccountCardCreatorParameters
import org.nypl.simplified.ui.accounts.AccountDetailFragment
import org.nypl.simplified.ui.accounts.AccountListFragment
import org.nypl.simplified.ui.accounts.AccountListRegistryFragment
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.nypl.simplified.ui.settings.SettingsCustomOPDSFragment
import org.nypl.simplified.ui.settings.SettingsDebugFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerModel
import org.nypl.simplified.ui.settings.SettingsMainFragment3
import java.util.LinkedList

object MainNavigation {

  fun openErrorPage(
    parameters: ErrorPageParameters
  ) {
    TODO()
  }

  object Settings {

    private val navigationStackAttribute: AttributeType<List<ScreenDefinitionType<*, *>>> =
      MainAttributes.attributes.withValue(listOf(SettingsMainFragment3.createScreenDefinition(Unit)))
    private val navigationStackAttributeUI: AttributeType<List<ScreenDefinitionType<*, *>>> =
      MainAttributes.attributes.withValue(listOf(SettingsMainFragment3.createScreenDefinition(Unit)))

    init {
      this.navigationStackAttribute.subscribe { _, x ->
        UIThread.runOnUIThread {
          this.navigationStackAttributeUI.set(x as List<ScreenDefinitionType<*, *>>)
        }
      }
    }

    val navigationStack: AttributeReadableType<List<ScreenDefinitionType<*, *>>> =
      this.navigationStackAttribute

    fun currentScreen(): ScreenDefinitionType<*, *> {
      return this.navigationStackAttributeUI.get().first()
    }

    private fun stackPush(
      screen: ScreenDefinitionType<*, *>
    ) {
      val existing = LinkedList(this.navigationStack.get())
      screen.setup()
      existing.push(screen)
      this.navigationStackAttribute.set(existing.toList())
    }

    private fun stackPop() {
      val existing = LinkedList(this.navigationStack.get())
      if (existing.size == 1) {
        return
      }
      val screen = existing.pop()
      screen.setup()
      this.navigationStackAttribute.set(existing.toList())
    }

    fun openCardCreator(
      parameters: AccountCardCreatorParameters
    ) {
      AccountCardCreatorModel.parameters = parameters
      TODO()
    }

    fun openDocument(
      documentTarget: SettingsDocumentViewerModel.DocumentTarget
    ) {
      this.stackPush(SettingsDocumentViewerFragment.createScreenDefinition(documentTarget))
    }

    fun openAccountList() {
      this.stackPush(AccountListFragment.createScreenDefinition(Unit))
    }

    fun openDebugSettings() {
      this.stackPush(SettingsDebugFragment.createScreenDefinition(Unit))
    }

    fun goUp() {
      this.stackPop()
    }

    fun openAccountCreationList() {
      this.stackPush(AccountListRegistryFragment.createScreenDefinition(Unit))
    }

    fun openAccountDetail(
      account: AccountType
    ) {
      this.stackPush(AccountDetailFragment.createScreenDefinition(account))
    }

    fun openCustomOPDS() {
      this.stackPush(SettingsCustomOPDSFragment.createScreenDefinition(Unit))
    }
  }
}
