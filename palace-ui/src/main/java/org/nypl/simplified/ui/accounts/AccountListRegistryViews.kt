package org.nypl.simplified.ui.accounts

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.settings.SettingsDebugModel
import org.nypl.simplified.ui.views.Views

class AccountListRegistryViews(
  private val accountList: RecyclerView,
  private val accountListAdapter: AccountProviderDescriptionListAdapter,
  private val activity: Activity,
  private val backButton: View,
  private val error: View,
  private val errorTouch: View,
  private val progress: ProgressBar,
  private val progressAnimated: View,
  private val rootView: View,
  private val searchIcon: ImageView,
  private val searchText: EditText,
  private val searchTouch: ViewGroup,
  private val swipe: SwipeRefreshLayout,
  private val title: TextView,
  private val toolbarTitle: TextView,
) {

  private val accountListFilterController =
    AccountListFilterController { items -> this.accountListAdapter.submitList(items) }

  companion object {
    fun create(
      context: Activity,
      rootView: View,
      accountListAdapter: AccountProviderDescriptionListAdapter,
      onSwipeTouched: () -> Unit,
    ): AccountListRegistryViews {
      val views =
        AccountListRegistryViews(
          activity = context,
          rootView = rootView,
          accountListAdapter = accountListAdapter,
          title = rootView.findViewById(R.id.accountRegistryTitle),
          toolbarTitle = rootView.findViewById(R.id.accountRegistryToolbarTitle),
          progress = rootView.findViewById(R.id.accountRegistryProgress),
          progressAnimated = rootView.findViewById(R.id.accountRegistryProgressAnimated),
          accountList = rootView.findViewById(R.id.accountRegistryList),
          swipe = rootView.findViewById(R.id.accountRegistryListRefresh),
          searchIcon = rootView.findViewById(R.id.accountRegistryToolbarSearchIcon),
          searchTouch = rootView.findViewById(R.id.accountRegistryToolbarSearchIconTouch),
          searchText = rootView.findViewById(R.id.accountRegistryToolbarSearchText),
          backButton = rootView.findViewById(R.id.accountRegistryToolbarBackIconTouch),
          error = rootView.findViewById(R.id.accountRegistryError),
          errorTouch = rootView.findViewById(R.id.accountRegistryErrorTouch)
        )

      views.accountList.setHasFixedSize(true)
      views.accountList.layoutManager = LinearLayoutManager(context)
      views.accountList.adapter = accountListAdapter
      views.accountList.addItemDecoration(SpaceItemDecoration(RecyclerView.VERTICAL, context))

      views.searchTouch.setOnClickListener {
        if (views.searchText.isVisible) {
          views.searchBoxClose()
        } else {
          views.searchBoxOpen()
        }
      }

      views.searchText.addTextChangedListener {
        views.updateAdapterFilter(views.searchText.text.trim().toString())
      }

      views.searchText.setOnEditorActionListener { v, actionId, event ->
        return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE) {
          views.keyboardHide()
          views.updateAdapterFilter(views.searchText.text.trim().toString())
          true
        } else {
          false
        }
      }

      views.swipe.setOnRefreshListener {
        views.showSwipeIndicator()
        onSwipeTouched.invoke()
      }
      return views
    }
  }

  fun hideAccountList() {
    Views.setVisible(this.accountList, false)
  }

  fun showAccountList() {
    Views.setVisible(this.accountList, true)
  }

  private fun updateAdapterFilter(
    text: String
  ) {
    if (text.isBlank()) {
      this.accountListFilterController.filterUnset()
    } else {
      this.accountListFilterController.filterSet { account ->
        account.title.contains(text, ignoreCase = true)
      }
    }
    this.accountList.scrollToPosition(0)
  }

  private fun showSwipeIndicator() {
    this.swipe.isRefreshing = true
    this.swipe.postDelayed({
      this.swipe.isRefreshing = false
    }, 1000L)
  }

  fun submitAccountProviderDescriptionList(
    descriptions: List<AccountProviderDescription>?
  ) {
    this.accountListFilterController.submit(descriptions ?: listOf())
  }

  fun reconfigureForRegistryStatus(
    status: AccountProviderRegistryStatus
  ) {
    UIThread.checkIsUIThread()

    return when (status) {
      is AccountProviderRegistryStatus.Idle -> {
        this.title.visibility = View.VISIBLE
        this.title.setText(R.string.accountRegistrySelect)

        Views.setVisible(this.error, false)
        Views.setVisible(this.progress, false)
        Views.setVisible(this.progressAnimated, false)
        Views.setVisible(this.title, true)
      }

      is AccountProviderRegistryStatus.Refreshing -> {
        val progressPercent = status.progressPercent
        if (progressPercent != null) {
          this.progress.isIndeterminate = false
          this.progress.setProgress(progressPercent.toInt(), true)
        } else {
          this.progress.isIndeterminate = true
        }

        Views.setVisible(this.error, false)
        Views.setVisible(this.progress, true)
        Views.setVisible(this.progressAnimated, true)
        Views.setVisible(this.title, false)
      }

      is AccountProviderRegistryStatus.Failed -> {
        Views.setVisible(this.error, true)
        Views.setVisible(this.progress, false)
        Views.setVisible(this.progressAnimated, false)
        Views.setVisible(this.title, false)

        this.errorTouch.setOnClickListener { openErrorPage(status.result) }
      }
    }
  }

  private fun openErrorPage(
    result: TaskResult<*>
  ) {
    val appVersion =
      SettingsDebugModel.appVersion()

    val supportEmail =
      Services.serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)
        .supportErrorReportEmailAddress

    MainNavigation.openErrorPage(
      activity = this.activity,
      parameters = ErrorPageParameters(
        emailAddress = supportEmail,
        body = result.message,
        subject = "[palace-error-report] $appVersion",
        attributes = result.attributes.toSortedMap(),
        taskSteps = result.steps
      )
    )
  }

  private fun searchBoxOpen() {
    val context = this.rootView.context

    this.searchIcon.setImageResource(R.drawable.xmark)
    this.searchText.visibility = View.VISIBLE
    this.toolbarTitle.visibility = View.INVISIBLE
    this.searchTouch.contentDescription =
      context.getString(R.string.settingsAccessibilitySearchButtonClose)

    this.searchText.postDelayed({ this.searchText.requestFocus() }, 100)
    this.searchText.postDelayed({ this.keyboardShow() }, 100)
  }

  private fun searchBoxClose() {
    val context = this.rootView.context

    this.searchIcon.setImageResource(R.drawable.magnifying_glass)
    this.searchText.visibility = View.INVISIBLE
    this.toolbarTitle.visibility = View.VISIBLE
    this.searchTouch.contentDescription =
      context.getString(R.string.settingsAccessibilitySearchButton)

    this.searchText.postDelayed({ this.keyboardHide() }, 100)
    this.searchText.setText("")
  }

  private fun keyboardHide() {
    try {
      WindowInsetsControllerCompat(this.activity.window, this.searchText)
        .hide(WindowInsetsCompat.Type.ime())
    } catch (e: Throwable) {
      // No sensible response.
    }
  }

  private fun keyboardShow() {
    try {
      WindowInsetsControllerCompat(this.activity.window, this.searchText)
        .show(WindowInsetsCompat.Type.ime())
    } catch (e: Throwable) {
      // No sensible response.
    }
  }

  fun setOnBackButtonListener(listener: (View) -> Unit) {
    this.backButton.setOnClickListener(listener::invoke::invoke)
  }

  fun setTitle(message: String) {
    this.title.text = message
  }
}
