package org.nypl.simplified.ui.accounts

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jattribute.core.AttributeType
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.errorpage.ErrorStrings
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainAttributes
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * A fragment that shows the account registry and allows for account creation.
 */

class AccountListRegistryFragment : Fragment(R.layout.account_list_registry) {

  private val logger =
    LoggerFactory.getLogger(AccountListRegistryFragment::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private lateinit var accountList: RecyclerView
  private lateinit var accountListAdapter: FilterableAccountListAdapter
  private lateinit var backButton: View
  private lateinit var progress: ContentLoadingProgressBar
  private lateinit var searchIcon: ImageView
  private lateinit var searchText: EditText
  private lateinit var searchTouch: ViewGroup
  private lateinit var swipe: SwipeRefreshLayout
  private lateinit var title: TextView
  private lateinit var toolbar: ViewGroup
  private lateinit var toolbarTitle: TextView

  private var errorDialog: AlertDialog? = null

  companion object : ScreenDefinitionFactoryType<Unit, AccountListRegistryFragment> {
    private class ScreenAccountRegistryList :
      ScreenDefinitionType<Unit, AccountListRegistryFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): AccountListRegistryFragment {
        return AccountListRegistryFragment()
      }
    }

    override fun createScreenDefinition(
      p: Unit
    ): ScreenDefinitionType<Unit, AccountListRegistryFragment> {
      return ScreenAccountRegistryList()
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.title =
      view.findViewById(R.id.accountRegistryTitle)
    this.toolbarTitle =
      view.findViewById(R.id.accountRegistryToolbarTitle)
    this.progress =
      view.findViewById(R.id.accountRegistryProgress)
    this.accountList =
      view.findViewById(R.id.accountRegistryList)
    this.toolbar =
      view.findViewById(R.id.accountRegistryToolbar)
    this.swipe =
      view.findViewById(R.id.accountRegistryListRefresh)
    this.searchIcon =
      view.findViewById(R.id.accountRegistryToolbarSearchIcon)
    this.searchTouch =
      view.findViewById(R.id.accountRegistryToolbarSearchIconTouch)
    this.searchText =
      view.findViewById(R.id.accountRegistryToolbarSearchText)
    this.backButton =
      view.findViewById(R.id.accountRegistryToolbarBackIconTouch)

    val services =
      Services.serviceDirectory()
    val imageLoader =
      services.requireService(ImageLoaderType::class.java)

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

    this.searchTouch.setOnClickListener {
      if (this.searchText.isVisible) {
        this.searchBoxClose()
      } else {
        this.searchBoxOpen()
      }
    }

    this.backButton.setOnClickListener {
      this.backButton.postDelayed(MainNavigation.Settings::goUp, 500)
    }

    this.searchText.addTextChangedListener {
      this.updateAdapterFilter(this.searchText.text.trim().toString())
    }

    this.searchText.setOnEditorActionListener { v, actionId, event ->
      return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE) {
        this.keyboardHide()
        this.updateAdapterFilter(this.searchText.text.trim().toString())
        true
      } else {
        false
      }
    }
  }

  private fun updateAdapterFilter(
    text: String
  ) {
    this.accountListAdapter.filterList { account ->
      if (account == null) {
        return@filterList true
      }
      if (text.isBlank()) {
        return@filterList true
      }
      if (account.title.contains(text, ignoreCase = true)) {
        return@filterList true
      }
      return@filterList false
    }
  }

  private fun searchBoxOpen() {
    this.searchIcon.setImageResource(R.drawable.xmark)
    this.searchText.visibility = View.VISIBLE
    this.toolbarTitle.visibility = View.INVISIBLE

    this.searchText.postDelayed({ this.searchText.requestFocus() }, 100)
    this.searchText.postDelayed({ this.keyboardShow() }, 100)
  }

  private fun searchBoxClose() {
    this.searchIcon.setImageResource(R.drawable.magnifying_glass)
    this.searchText.visibility = View.INVISIBLE
    this.toolbarTitle.visibility = View.VISIBLE

    this.searchText.postDelayed({ this.keyboardHide() }, 100)
  }

  private fun keyboardHide() {
    try {
      WindowInsetsControllerCompat(this.requireActivity().window, this.searchText)
        .hide(WindowInsetsCompat.Type.ime())
    } catch (e: Throwable) {
      // No sensible response.
    }
  }

  private fun keyboardShow() {
    try {
      WindowInsetsControllerCompat(this.requireActivity().window, this.searchText)
        .show(WindowInsetsCompat.Type.ime())
    } catch (e: Throwable) {
      // No sensible response.
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
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    /*
     * The account registry doesn't guarantee event delivery on the UI thread, so we wrap the
     * attributes here for use.
     */

    val registryStatusUI: AttributeType<AccountProviderRegistryStatus> =
      MainAttributes.attributes.withValue(AccountProviderRegistryStatus.Idle)
    val accountProvidersUI: AttributeType<Map<URI, AccountProviderDescription>> =
      MainAttributes.attributes.withValue(mapOf())

    this.subscriptions.add(
      MainAttributes.wrapAttribute(
        source = registry.statusAttribute,
        target = registryStatusUI
      )
    )
    this.subscriptions.add(
      MainAttributes.wrapAttribute(
        source = registry.accountProviderDescriptionsAttribute,
        target = accountProvidersUI
      )
    )

    this.subscriptions.add(
      registryStatusUI.subscribe { _, status ->
        this.reconfigureViewForRegistryStatus(status)
      }
    )
    this.subscriptions.add(
      accountProvidersUI.subscribe { _, providers ->
        this.accountListAdapter.submitList(
          providers.values.toList()
            .sortedWith(AccountProviderDescriptionComparator(buildConfig))
        )
      }
    )

    val accountEvents =
      services.requireService(AccountEvents::class.java)
    val accountSub =
      accountEvents.events.subscribe(this::onAccountEvent)

    this.subscriptions.add(AutoCloseable {
      accountSub.dispose()
    })

    this.swipe.setOnRefreshListener {
      registry.refreshAsync(
        profiles.profileCurrent()
          .preferences()
          .showTestingLibraries
      )
    }
  }

  @UiThread
  private fun onAccountEvent(
    event: AccountEvent
  ) {
    UIThread.checkIsUIThread()

    when (event) {
      is AccountEventCreation.AccountEventCreationInProgress -> {
        this.accountList.visibility = View.INVISIBLE
        this.progress.show()
        this.title.text = event.message
      }

      is AccountEventCreation.AccountEventCreationSucceeded -> {
        MainNavigation.Settings.goUp()
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
        this.swipe.isRefreshing = false
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
          .setPositiveButton(ErrorStrings.errorDetails) { dialog, _ ->
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

    MainNavigation.openErrorPage(
      activity = this.requireActivity(),
      parameters = parameters
    )
  }
}
