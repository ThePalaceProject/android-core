package org.nypl.simplified.ui.accounts

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jattribute.core.AttributeType
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.android.schedulers.AndroidSchedulers
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainAttributes
import org.nypl.simplified.ui.main.MainNavigation
import org.slf4j.LoggerFactory

/**
 * A fragment that shows the account registry and allows for account creation.
 */

class AccountListRegistryFragment : Fragment(R.layout.account_list_registry) {

  private val logger =
    LoggerFactory.getLogger(AccountListRegistryFragment::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private lateinit var toolbar: ViewGroup
  private lateinit var accountList: RecyclerView
  private lateinit var accountListAdapter: FilterableAccountListAdapter
  private lateinit var progress: ContentLoadingProgressBar
  private lateinit var title: TextView
  private var errorDialog: AlertDialog? = null

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.title =
      view.findViewById(R.id.accountRegistryTitle)
    this.progress =
      view.findViewById(R.id.accountRegistryProgress)
    this.accountList =
      view.findViewById(R.id.accountRegistryList)
    this.toolbar =
      view.findViewById(R.id.accountListToolbar)

    val imageLoader =
      Services.serviceDirectory()
        .requireService(ImageLoaderType::class.java)

    this.accountListAdapter =
      FilterableAccountListAdapter(
        imageLoader,
        this::onAccountClicked
      )

    with(this.accountList) {
      this.setHasFixedSize(true)
      this.layoutManager = LinearLayoutManager(this.context)
      this.adapter = this@AccountListRegistryFragment.accountListAdapter
      this.addItemDecoration(
        SpaceItemDecoration(
          RecyclerView.VERTICAL,
          this@AccountListRegistryFragment.requireContext()
        )
      )
    }
  }

  private fun onAccountClicked(
    account: AccountProviderDescription
  ) {
    this.logger.debug("selected account: {} ({})", account.id, account.title)

    this.accountList.visibility = View.INVISIBLE
    this.title.setText(R.string.accountRegistryCreating)
    this.progress.show()

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    profiles.profileAccountCreate(account.id)
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()

    val services =
      Services.serviceDirectory()
    val registry =
      services.requireService(AccountProviderRegistryType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    val registryStatusUI: AttributeType<AccountProviderRegistryStatus> =
      MainAttributes.attributes.withValue(AccountProviderRegistryStatus.Idle)

    this.subscriptions.add(
      MainAttributes.wrapAttribute(
        source = registry.statusAttribute,
        target = registryStatusUI
      )
    )
    this.subscriptions.add(
      registryStatusUI.subscribe { _, status ->
        this.reconfigureViewForRegistryStatus(status)
      }
    )

    val accountSub =
      profiles.accountEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountEvent)

    this.subscriptions.add(AutoCloseable {
      accountSub.dispose()
    })
  }

  private fun onAccountEvent(
    event: AccountEvent
  ) {
    when (event) {
      is AccountEventCreation.AccountEventCreationInProgress -> {
        this.accountList.visibility = View.INVISIBLE
        this.progress.show()
        this.title.text = event.message
      }

      is AccountEventCreation.AccountEventCreationSucceeded -> {
        TODO()
      }

      is AccountEventCreation.AccountEventCreationFailed -> {
        this.showAccountCreationFailedDialog(event)
      }
    }
  }

  private fun reconfigureViewForRegistryStatus(
    status: AccountProviderRegistryStatus
  ) {
    return when (status) {
      AccountProviderRegistryStatus.Idle -> {
        this.title.setText(R.string.accountRegistrySelect)
        this.accountList.visibility = View.VISIBLE
        this.progress.hide()
      }

      AccountProviderRegistryStatus.Refreshing -> {
        this.title.setText(R.string.accountRegistrySelect)
        this.accountList.visibility = View.INVISIBLE
        this.progress.show()
        this.title.setText(R.string.accountRegistryRetrieving)
      }
    }
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }

  private fun showAccountCreationFailedDialog(
    accountEvent: AccountEventCreation.AccountEventCreationFailed
  ) {
    this.logger.debug("showAccountCreationFailedDialog")

    if (this.errorDialog == null) {
      val newDialog =
        MaterialAlertDialogBuilder(this.requireActivity())
          .setTitle(R.string.accountCreationFailed)
          .setMessage(R.string.accountCreationFailedMessage)
          .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
            this.errorDialog = null
            this.showErrorPage(accountEvent)
            dialog.dismiss()
          }.create()
      this.errorDialog = newDialog
      newDialog.show()
    }
  }

  private fun showErrorPage(
    accountEvent: AccountEventCreation.AccountEventCreationFailed
  ) {
    val buildConfig =
      Services.serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)

    val parameters =
      ErrorPageParameters(
        emailAddress = buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[palace-error-report]",
        attributes = accountEvent.attributes.toSortedMap(),
        taskSteps = accountEvent.taskResult.steps
      )

    MainNavigation.openErrorPage(parameters)
  }
}
