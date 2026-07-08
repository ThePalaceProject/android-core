package org.nypl.simplified.ui.accounts

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.database.api.AccountsDatabaseDuplicateProviderException
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryRefresh
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.errorpage.ErrorStrings
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory

/**
 * A fragment that shows the account registry and allows for account creation.
 */

class AccountListRegistryFragment :
  Fragment(R.layout.account_list_registry),
  MainBackButtonConsumerType {
  private val logger =
    LoggerFactory.getLogger(AccountListRegistryFragment::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private lateinit var accountListViews: AccountListRegistryViews
  private lateinit var accountListAdapter: AccountProviderDescriptionListAdapter

  private var errorDialog: AlertDialog? = null

  companion object : ScreenDefinitionFactoryType<Unit, AccountListRegistryFragment> {
    private class ScreenAccountRegistryList : ScreenDefinitionType<Unit, AccountListRegistryFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() = Unit

      override fun fragment(): AccountListRegistryFragment = AccountListRegistryFragment()
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, AccountListRegistryFragment> = ScreenAccountRegistryList()
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    val services =
      Services.serviceDirectory()
    val imageLoader =
      services.requireService(ImageLoaderType::class.java)
    val registry =
      services.requireService(AccountProviderRegistryType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    this.accountListAdapter =
      AccountProviderDescriptionListAdapter(imageLoader, this::onAccountClicked)

    this.accountListViews =
      AccountListRegistryViews.create(
        context = this.requireActivity(),
        rootView = view,
        accountListAdapter = this.accountListAdapter,
        onSwipeTouched = {
          registry.refreshAsync(
            AccountProviderRegistryRefresh.Full(
              clearBeforeRefresh = true,
              includeTestingLibraries = profiles.profileCurrent().preferences().showTestingLibraries
            )
          )
        }
      )

    this.accountListViews.setOnBackButtonListener { button ->
      button.postDelayed(MainNavigation.Settings::goUp, 500)
    }
  }

  private fun onAccountClicked(account: AccountProviderDescription) {
    this.logger.debug("selected account: {} ({})", account.id, account.title)

    this.accountListViews.hideAccountList()
    this.accountListViews.setTitle(this.getString(R.string.accountRegistryCreating))

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    profiles.profileAccountCreate(account.id)

    val activity = this.requireActivity()
    val toast =
      Toast.makeText(
        activity,
        activity.resources.getString(R.string.settingsAddedLibrary, account.title),
        Toast.LENGTH_LONG
      )
    toast.show()
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()

    val services =
      Services.serviceDirectory()
    val registry =
      services.requireService(AccountProviderRegistryType::class.java)

    val accountEvents =
      services.requireService(AccountEvents::class.java)
    val accountSub =
      accountEvents.events.subscribe(this::onAccountEvent)

    this.subscriptions.add(
      registry.accountProviderDescriptionsSortedAttribute.subscribe { _, descriptions ->
        this.accountListViews.submitAccountProviderDescriptionList(descriptions)
      }
    )
    this.subscriptions.add(
      registry.statusAttribute.subscribe { _, status ->
        this.accountListViews.reconfigureForRegistryStatus(status)
      }
    )

    this.subscriptions.add(
      AutoCloseable {
        accountSub.dispose()
      }
    )

    registry.loadAsync()
  }

  @UiThread
  private fun onAccountEvent(event: AccountEvent) {
    UIThread.checkIsUIThread()

    when (event) {
      is AccountEventCreation.AccountEventCreationInProgress -> {
        this.accountListViews.hideAccountList()
        this.accountListViews.setTitle(event.message)
      }

      is AccountEventCreation.AccountEventCreationSucceeded -> {
        MainNavigation.Settings.goUp()
      }

      is AccountEventCreation.AccountEventCreationFailed -> {
        if (event.exception is AccountsDatabaseDuplicateProviderException) {
          MainNavigation.Settings.goUp()
          return
        }
        this.showAccountCreationFailedDialog(event)
      }
    }
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }

  private fun showAccountCreationFailedDialog(accountEvent: AccountEventCreation.AccountEventCreationFailed) {
    this.logger.debug("showAccountCreationFailedDialog")

    if (this.errorDialog == null) {
      val newDialog =
        MaterialAlertDialogBuilder(this.requireActivity())
          .setTitle(R.string.accountCreationFailed)
          .setMessage(R.string.accountCreationFailedMessage)
          .setNegativeButton(R.string.Dismiss) { dialog, _ ->
            this.errorDialog = null
            dialog.dismiss()
          }.setPositiveButton(ErrorStrings.errorDetails) { dialog, _ ->
            this.errorDialog = null
            this.showErrorPage(accountEvent)
            dialog.dismiss()
          }.create()
      this.errorDialog = newDialog
      newDialog.show()
    }
  }

  private fun showErrorPage(accountEvent: AccountEventCreation.AccountEventCreationFailed) {
    val buildConfig =
      Services
        .serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)

    val parameters =
      ErrorPageParameters(
        emailAddress = buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[palace-error-report]",
        attributes = accountEvent.attributes.toSortedMap(),
        taskSteps = accountEvent.taskResult.steps
      )

    MainNavigation.openErrorPage(
      activity = this.requireActivity(),
      parameters = parameters
    )
  }

  override fun onBackButtonPressed(): Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }
}
