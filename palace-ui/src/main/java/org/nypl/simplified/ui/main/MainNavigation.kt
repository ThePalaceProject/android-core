package org.nypl.simplified.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountCardCreatorFragment
import org.nypl.simplified.ui.accounts.AccountCardCreatorParameters
import org.nypl.simplified.ui.accounts.AccountDetailFragment
import org.nypl.simplified.ui.accounts.AccountDetailModel
import org.nypl.simplified.ui.accounts.AccountListFragment
import org.nypl.simplified.ui.accounts.AccountListRegistryFragment
import org.nypl.simplified.ui.catalog.CatalogPart
import org.nypl.simplified.ui.errorpage.ErrorPageActivity
import org.nypl.simplified.ui.errorpage.ErrorPageModel
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainTabCategory.TAB_SETTINGS
import org.nypl.simplified.ui.main.MainTabRequest.TabAny
import org.nypl.simplified.ui.main.MainTabRequest.TabForCategory
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.nypl.simplified.ui.settings.SettingsCustomOPDSFragment
import org.nypl.simplified.ui.settings.SettingsDebugMenuBooksFragment
import org.nypl.simplified.ui.settings.SettingsDebugMenuDRMFragment
import org.nypl.simplified.ui.settings.SettingsDebugMenuErrorsFragment
import org.nypl.simplified.ui.settings.SettingsDebugMenuFragment
import org.nypl.simplified.ui.settings.SettingsDebugMenuRegistryFragment
import org.nypl.simplified.ui.settings.SettingsDebugMenuStartupFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerModel
import org.nypl.simplified.ui.settings.SettingsMainFragment3
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicReference

object MainNavigation {

  private val logger =
    LoggerFactory.getLogger(MainNavigation::class.java)

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
    ErrorPageModel.parameters = parameters
    activity.startActivity(Intent(activity, ErrorPageActivity::class.java))
  }

  @UiThread
  fun showLoginDialog(
    account: AccountType
  ) {
    UIThread.checkIsUIThread()
    this.tabAttribute.set(TabForCategory(TAB_SETTINGS))
    Settings.openAccountDetail(account, showLoginReason = AccountDetailModel.PleaseLoginReasonGeneric)
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
    try {
      val i = Intent(Intent.ACTION_VIEW)
      i.setData(Uri.parse(target.toString()))
      activity.startActivity(i)
    } catch (e: Throwable) {
      this.logger.error("Unable to open web view activity: ", e)
      try {
        Toast.makeText(activity, "Unable to open web view activity.", Toast.LENGTH_SHORT)
          .show()
      } catch (e: Throwable) {
        // Nothing we can do about this.
      }
    }
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
      this.takeAndExecuteOnClose()
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
      this.stackPush(SettingsDebugMenuFragment.createScreenDefinition(Unit))
    }

    fun openDebugDRMSettings() {
      this.stackPush(SettingsDebugMenuDRMFragment.createScreenDefinition(Unit))
    }

    fun openDebugErrorsSettings() {
      this.stackPush(SettingsDebugMenuErrorsFragment.createScreenDefinition(Unit))
    }

    fun openDebugRegistrySettings() {
      this.stackPush(SettingsDebugMenuRegistryFragment.createScreenDefinition(Unit))
    }

    fun openDebugStartupSettings() {
      this.stackPush(SettingsDebugMenuStartupFragment.createScreenDefinition(Unit))
    }

    fun goUp() {
      this.stackPop()
    }

    fun openAccountCreationList() {
      this.stackPush(AccountListRegistryFragment.createScreenDefinition(Unit))
    }

    fun openAccountDetail(
      account: AccountType,
      showLoginReason: AccountDetailModel.PleaseLoginReason?
    ) {
      this.stackPush(
        AccountDetailFragment.createScreenDefinition(
          AccountDetailFragment.AccountDetailScreenParameters(
            account = account,
            showLoginReason = showLoginReason
          )
        )
      )
    }

    fun openBooks() {
      this.stackPush(SettingsDebugMenuBooksFragment.createScreenDefinition(Unit))
    }

    fun openCustomOPDS() {
      this.stackPush(SettingsCustomOPDSFragment.createScreenDefinition(Unit))
    }

    fun goToRoot() {
      this.navigationStackAttribute.set(
        listOf(SettingsMainFragment3.createScreenDefinition(Unit))
      )
    }

    private val onClose =
      AtomicReference<Runnable>(Runnable { })

    /**
     * Set a runnable that will be executed when the current settings screen is closed.
     */

    fun setOnClose(runnable: Runnable) {
      this.onClose.set(runnable)
    }

    /**
     * Take any existing "on close" runnable and set the new runnable to a no-op expression,
     * and then run the existing runnable.
     */

    private fun takeAndExecuteOnClose() {
      val task = this.onClose.getAndSet(Runnable { })
      UIThread.runOnUIThread(task)
    }
  }
}
