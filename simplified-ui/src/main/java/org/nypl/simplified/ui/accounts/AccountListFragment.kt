package org.nypl.simplified.ui.accounts

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.android.schedulers.AndroidSchedulers
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountEventDeletion
import org.nypl.simplified.accounts.api.AccountEventDeletion.AccountEventDeletionFailed
import org.nypl.simplified.accounts.api.AccountEventUpdated
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.errorpage.ErrorStrings
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import java.net.URI

/**
 * A fragment that shows the set of accounts in the current profile.
 */

class AccountListFragment : Fragment(R.layout.account_list) {

  private var subscriptions =
    CloseableCollection.create()

  private lateinit var addButton: View
  private lateinit var backButton: View
  private lateinit var accountList: RecyclerView
  private lateinit var accountListAdapter: AccountListAdapter

  companion object : ScreenDefinitionFactoryType<Unit, AccountListFragment> {
    private class ScreenAccountList : ScreenDefinitionType<Unit, AccountListFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): AccountListFragment {
        return AccountListFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, AccountListFragment> {
      return ScreenAccountList()
    }
  }

  private fun onAccountDeleteClicked(
    account: AccountType
  ) {
    val context = this.requireContext()
    val dialog = MaterialAlertDialogBuilder(context)
      .setTitle(R.string.accountsDeleteConfirmTitle)
      .setMessage(
        context.getString(
          R.string.accountsDeleteConfirm,
          account.provider.displayName
        )
      )
      .setNegativeButton(R.string.accountCancel) { dialog, _ ->
        dialog.dismiss()
      }
      .setPositiveButton(R.string.accountsDelete) { dialog, _ ->
        this.deleteAccountByProvider(account.provider.id)
        dialog.dismiss()
      }
      .create()

    /*
     * Set content descriptions for buttons to improve accessibility and automated retrieval
     * by unit tests. Note that this has to be done after `show()` otherwise `getButton` often
     * returns `null`.
     */

    dialog.show()
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription =
      context.getString(R.string.accountCancel)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription =
      context.getString(R.string.accountsDelete)
  }

  private fun deleteAccountByProvider(
    id: URI
  ) {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    profiles.profileAccountDeleteByProvider(id)
  }

  private fun onAccountClicked(
    account: AccountType
  ) {
    MainNavigation.Settings.openAccountDetail(account)
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.accountList =
      view.findViewById(R.id.accountList)
    this.backButton =
      view.findViewById(R.id.accountListToolbarBackIconTouch)
    this.addButton =
      view.findViewById(R.id.accountListToolbarAddIconTouch)
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()

    this.backButton.setOnClickListener {
      this.backButton.postDelayed(MainNavigation.Settings::goUp, 500)
    }
    this.addButton.setOnClickListener {
      this.addButton.postDelayed(MainNavigation.Settings::openAccountCreationList, 500)
    }

    val services =
      Services.serviceDirectory()
    val imageLoader =
      services.requireService(ImageLoaderType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    this.accountListAdapter =
      AccountListAdapter(
        imageLoader = imageLoader,
        onItemClicked = this::onAccountClicked,
        onItemDeleteClicked = this::onAccountDeleteClicked
      )

    this.updateAccountList()

    with(this.accountList) {
      this.setHasFixedSize(true)
      this.layoutManager = LinearLayoutManager(this.context)
      (this.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      this.adapter = this@AccountListFragment.accountListAdapter
    }

    val accountSub =
      profiles.accountEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountEvent)

    this.subscriptions.add(AutoCloseable { accountSub.dispose() })
  }

  private fun updateAccountList() {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val profile =
      profiles.profileCurrent()

    this.accountListAdapter.submitList(
      profile.accounts()
        .values
        .sortedWith(AccountComparator())
    )
  }

  @UiThread
  private fun onAccountEvent(
    accountEvent: AccountEvent
  ) {
    UIThread.checkIsUIThread()

    when (accountEvent) {
      is AccountEventDeletionFailed -> {
        this.showAccountDeletionFailedDialog(accountEvent)
      }

      is AccountEventCreation.AccountEventCreationSucceeded,
      is AccountEventDeletion.AccountEventDeletionSucceeded,
      is AccountEventUpdated -> {
        this.updateAccountList()
      }
    }
  }

  @UiThread
  private fun showAccountDeletionFailedDialog(
    accountEvent: AccountEventDeletionFailed
  ) {
    MaterialAlertDialogBuilder(this.requireContext())
      .setTitle(R.string.accountsDeletionFailed)
      .setMessage(R.string.accountsDeletionFailedMessage)
      .setPositiveButton(ErrorStrings.errorDetails) { _, _ ->
        this.showErrorPage(accountEvent)
      }
      .create()
      .show()
  }

  private fun showErrorPage(
    accountEvent: AccountEventDeletionFailed
  ) {
    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    MainNavigation.openErrorPage(
      ErrorPageParameters(
        emailAddress = buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[palace-error-report]",
        attributes = accountEvent.attributes.toSortedMap<String, String>(),
        taskSteps = accountEvent.taskResult.steps
      )
    )
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }
}
