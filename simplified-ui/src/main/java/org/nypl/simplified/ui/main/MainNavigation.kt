package org.nypl.simplified.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountCardCreatorFragment
import org.nypl.simplified.ui.accounts.AccountCardCreatorParameters
import org.nypl.simplified.ui.accounts.AccountDetailFragment
import org.nypl.simplified.ui.accounts.AccountListFragment
import org.nypl.simplified.ui.accounts.AccountListRegistryFragment
import org.nypl.simplified.ui.catalog.CatalogPart
import org.nypl.simplified.ui.errorpage.ErrorPageActivity
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainTabCategory.TAB_SETTINGS
import org.nypl.simplified.ui.main.MainTabRequest.TabAny
import org.nypl.simplified.ui.main.MainTabRequest.TabForCategory
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.nypl.simplified.ui.settings.SettingsCustomOPDSFragment
import org.nypl.simplified.ui.settings.SettingsDebugFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerModel
import org.nypl.simplified.ui.settings.SettingsMainFragment3
import java.net.URI
import java.util.LinkedList

object MainNavigation {

  private val tabAttribute: AttributeType<MainTabRequest> =
    MainAttributes.attributes.withValue(TabAny)
  private val tabAttributeUI: AttributeType<MainTabRequest> =
    MainAttributes.attributes.withValue(TabAny)

  init {
    this.tabAttribute.subscribe { _, x ->
      UIThread.runOnUIThread {
        this.tabAttributeUI.set(x)
      }
    }
  }

  val tab: AttributeReadableType<MainTabRequest> =
    this.tabAttributeUI

  @UiThread
  fun openErrorPage(
    activity: Activity,
    parameters: ErrorPageParameters
  ) {
    UIThread.checkIsUIThread()
    ErrorPageActivity.show(activity, parameters)
  }

  @UiThread
  fun showLoginDialog(
    account: AccountType
  ) {
    UIThread.checkIsUIThread()
    this.tabAttribute.set(TabForCategory(TAB_SETTINGS))
    Settings.openAccountDetail(account, showLoginTitle = true)
    this.tabAttribute.set(TabAny)
  }

  @UiThread
  fun requestTabChange(
    category: MainTabCategory
  ) {
    UIThread.checkIsUIThread()
    this.tabAttribute.set(TabForCategory(category))
    this.tabAttribute.set(TabAny)
  }

  @UiThread
  fun requestTabChangeForPart(
    catalogPart: CatalogPart
  ) {
    UIThread.checkIsUIThread()
    this.requestTabChange(MainTabCategory.forPart(catalogPart))
  }

  @UiThread
  fun openAccountCreation() {
    UIThread.checkIsUIThread()
    this.tabAttribute.set(TabForCategory(TAB_SETTINGS))
    Settings.openAccountCreationList()
    this.tabAttribute.set(TabAny)
  }

  @UiThread
  fun openExternalBrowser(
    activity: FragmentActivity,
    target: URI
  ) {
    UIThread.checkIsUIThread()
    val i = Intent(Intent.ACTION_VIEW)
    i.setData(Uri.parse(target.toString()))
    activity.startActivity(i)
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
      this.stackPush(AccountCardCreatorFragment.createScreenDefinition(parameters))
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
      account: AccountType,
      showLoginTitle: Boolean
    ) {
      this.stackPush(
        AccountDetailFragment.createScreenDefinition(
          AccountDetailFragment.AccountDetailScreenParameters(
            account = account,
            showLoginTitle = showLoginTitle
          )
        )
      )
    }

    fun openCustomOPDS() {
      this.stackPush(SettingsCustomOPDSFragment.createScreenDefinition(Unit))
    }
  }
}
