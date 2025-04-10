package org.librarysimplified.main

import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.ui.catalog.CatalogBookDetailEvent
import org.librarysimplified.ui.catalog.CatalogBookDetailFragment
import org.librarysimplified.ui.catalog.CatalogBookDetailFragmentParameters
import org.librarysimplified.ui.catalog.CatalogFeedArguments
import org.librarysimplified.ui.catalog.CatalogFeedEvent
import org.librarysimplified.ui.catalog.CatalogFeedFragment
import org.librarysimplified.ui.catalog.saml20.CatalogSAML20Event
import org.librarysimplified.ui.navigation.tabs.TabbedNavigator
import org.librarysimplified.viewer.preview.BookPreviewActivity
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountCardCreatorFragment
import org.nypl.simplified.ui.accounts.AccountCardCreatorParameters
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.nypl.simplified.ui.accounts.AccountDetailFragment
import org.nypl.simplified.ui.accounts.AccountFragmentParameters
import org.nypl.simplified.ui.accounts.AccountListEvent
import org.nypl.simplified.ui.accounts.AccountListFragment
import org.nypl.simplified.ui.accounts.AccountListFragmentParameters
import org.nypl.simplified.ui.accounts.AccountListRegistryEvent
import org.nypl.simplified.ui.accounts.AccountListRegistryFragment
import org.nypl.simplified.ui.accounts.AccountPickerEvent
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20Event
import org.nypl.simplified.ui.errorpage.ErrorPageEvent
import org.nypl.simplified.ui.errorpage.ErrorPageFragment
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.settings.SettingsCustomOPDSFragment
import org.nypl.simplified.ui.settings.SettingsDebugEvent
import org.nypl.simplified.ui.settings.SettingsDebugFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerEvent
import org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment
import org.nypl.simplified.ui.settings.SettingsMainEvent
import org.nypl.simplified.viewer.api.Viewers
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.slf4j.LoggerFactory
import java.net.URL

internal class MainFragmentListenerDelegate(
  private val fragment: Fragment,
  private val listenerRepository: ListenerRepository<MainFragmentListenedEvent, MainFragmentState>,
  private val navigator: TabbedNavigator,
  private val profilesController: ProfilesControllerType,
  private val settingsConfiguration: BuildConfigurationServiceType
) : LifecycleObserver {
  private val logger =
    LoggerFactory.getLogger(MainFragmentListenerDelegate::class.java)

  private val subscriptions =
    CompositeDisposable()

  @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
  fun onCreate() {
    this.configureToolbar()

    val activity = this.fragment.requireActivity() as AppCompatActivity

    activity.onBackPressedDispatcher.addCallback(this.fragment) {
      if (navigator.popBackStack()) {
        return@addCallback
      }

      try {
        isEnabled = false
        activity.onBackPressed()
      } finally {
        isEnabled = true
      }
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  fun onStart() {
    this.listenerRepository.registerHandler(this::handleEvent)

    this.navigator.infoStream
      .subscribe { action ->
        this.logger.debug(action.toString())
        this.onFragmentTransactionCompleted()
      }
      .let { subscriptions.add(it) }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  fun onStop() {
    this.listenerRepository.unregisterHandler()
    subscriptions.clear()
  }

  private fun onFragmentTransactionCompleted() {
    val isRoot = (0 == this.navigator.backStackSize())
    this.logger.debug(
      "controller stack size changed [{}, isRoot={}]", this.navigator.backStackSize(), isRoot
    )
    configureToolbar()
  }

  private fun configureToolbar() {
    val isRoot = (0 == this.navigator.backStackSize())
    val activity = this.fragment.requireActivity() as AppCompatActivity
    activity.supportActionBar?.apply {
      setHomeAsUpIndicator(null)
      setHomeActionContentDescription(null)
      setDisplayHomeAsUpEnabled(!isRoot)
    }
  }

  private fun handleEvent(
    event: MainFragmentListenedEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is MainFragmentListenedEvent.CatalogSAML20Event ->
        this.handleCatalogSAML20Event(event.event, state)

      is MainFragmentListenedEvent.CatalogFeedEvent ->
        this.handleCatalogFeedEvent(event.event, state)

      is MainFragmentListenedEvent.CatalogBookDetailEvent ->
        this.handleCatalogBookDetailEvent(event.event, state)

      is MainFragmentListenedEvent.SettingsMainEvent ->
        this.handleSettingsMainEvent(event.event, state)

      is MainFragmentListenedEvent.SettingsDebugEvent ->
        this.handleSettingsDebugEvent(event.event, state)

      is MainFragmentListenedEvent.SettingsDocumentViewerEvent ->
        this.handleSettingsDocumentViewerEvent(event.event, state)

      is MainFragmentListenedEvent.AccountListRegistryEvent ->
        this.handleAccountListRegistryEvent(event.event, state)

      is MainFragmentListenedEvent.AccountListEvent ->
        this.handleAccountListEvent(event.event, state)

      is MainFragmentListenedEvent.AccountDetailEvent ->
        this.handleAccountEvent(event.event, state)

      is MainFragmentListenedEvent.AccountSAML20Event ->
        this.handleAccountSAML20Event(event.event, state)

      is MainFragmentListenedEvent.AccountPickerEvent ->
        this.handleAccountPickerEvent(event.event, state)

      is MainFragmentListenedEvent.ErrorPageEvent ->
        this.handleErrorPageEvent(event.event, state)
    }
  }

  private fun handleCatalogSAML20Event(
    event: CatalogSAML20Event,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is CatalogSAML20Event.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      CatalogSAML20Event.LoginSucceeded -> {
        this.popBackStack()
        state
      }
    }
  }

  private fun handleCatalogFeedEvent(
    event: CatalogFeedEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is CatalogFeedEvent.LoginRequired -> {
        this.openSettingsAccount(
          event.account,
          comingFromBookLoanRequest = true,
          comingFromDeepLink = false,
          barcode = null
        )
        MainFragmentState.CatalogWaitingForLogin
      }

      is CatalogFeedEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is CatalogFeedEvent.OpenViewer -> {
        this.openViewer(event.book, event.format)
        state
      }

      is CatalogFeedEvent.OpenBookDetail -> {
        this.openBookDetail(event.feedArguments, event.opdsEntry)
        state
      }

      is CatalogFeedEvent.OpenFeed -> {
        this.openFeed(event.feedArguments)
        state
      }

      CatalogFeedEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleCatalogBookDetailEvent(
    event: CatalogBookDetailEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is CatalogBookDetailEvent.LoginRequired -> {
        this.openSettingsAccount(
          event.account,
          comingFromBookLoanRequest = true,
          comingFromDeepLink = false,
          barcode = null
        )
        MainFragmentState.BookDetailsWaitingForLogin
      }

      is CatalogBookDetailEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is CatalogBookDetailEvent.OpenViewer -> {
        this.openViewer(event.book, event.format)
        state
      }

      is CatalogBookDetailEvent.OpenFeed -> {
        this.openFeed(event.feedArguments)
        state
      }

      is CatalogBookDetailEvent.OpenBookDetail -> {
        this.openBookDetail(event.feedArguments, event.opdsEntry)
        state
      }

      is CatalogBookDetailEvent.OpenPreviewViewer -> {
        this.openPreviewViewer(event.feedEntry)
        state
      }

      CatalogBookDetailEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun openPreviewViewer(feedEntry: FeedEntry.FeedEntryOPDS) {
    BookPreviewActivity.startActivity(
      fragment.requireActivity(),
      feedEntry
    )
  }

  private fun handleAccountListRegistryEvent(
    event: AccountListRegistryEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is AccountListRegistryEvent.AccountCreated -> {
        this.setMostRecentAccount(event.accountID)
        this.popBackStack()
        this.openCatalog()
        state
      }

      is AccountListRegistryEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is AccountListRegistryEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun setMostRecentAccount(accountID: AccountID) {
    this.profilesController.profileUpdate { description ->
      description.copy(preferences = description.preferences.copy(mostRecentAccount = accountID))
    }.get()
  }

  private fun handleAccountListEvent(
    event: AccountListEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is AccountListEvent.AccountSelected -> {
        this.openSettingsAccount(
          accountID = event.accountID,
          comingFromBookLoanRequest = false,
          comingFromDeepLink = event.comingFromDeepLink,
          barcode = event.barcode
        )
        state
      }

      AccountListEvent.AddAccount -> {
        this.openAccountRegistry(tab = org.librarysimplified.ui.tabs.R.id.tabSettings)
        state
      }

      is AccountListEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      AccountListEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleAccountEvent(
    event: AccountDetailEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      AccountDetailEvent.LoginSucceeded ->
        when (state) {
          is MainFragmentState.CatalogWaitingForLogin,
          is MainFragmentState.BookDetailsWaitingForLogin -> {
            this.navigator.popBackStack()
            MainFragmentState.EmptyState
          }

          else -> {
            state
          }
        }

      is AccountDetailEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is AccountDetailEvent.OpenSAML20Login -> {
        this.openSAML20Login()
        state
      }

      is AccountDetailEvent.OpenDocViewer -> {
        this.openDocViewer(event.title, event.url)
        state
      }

      is AccountDetailEvent.OpenWebView -> {
        this.openCardCreatorWebView(event.parameters)
        state
      }

      AccountDetailEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleAccountSAML20Event(
    event: AccountSAML20Event,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      AccountSAML20Event.AccessTokenObtained -> {
        this.popBackStack()
        state
      }

      is AccountSAML20Event.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }
    }
  }

  private fun handleAccountPickerEvent(
    event: AccountPickerEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is AccountPickerEvent.AccountSelected -> {
        // TODO: this should work without this for now
        state
      }

      AccountPickerEvent.AddAccount -> {
        this.openAccountRegistry(tab = org.librarysimplified.ui.tabs.R.id.tabCatalog)
        state
      }
    }
  }

  private fun handleErrorPageEvent(
    event: ErrorPageEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      ErrorPageEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleSettingsMainEvent(
    event: SettingsMainEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is SettingsMainEvent.OpenAbout -> {
        this.openSettingsAbout(event.title, event.url)
        state
      }

      SettingsMainEvent.OpenAccountList -> {
        this.openSettingsAccounts()
        state
      }

      SettingsMainEvent.OpenDebugOptions -> {
        this.openSettingsVersion()
        state
      }

      is SettingsMainEvent.OpenEULA -> {
        this.openSettingsEULA(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenFAQ -> {
        this.openSettingsFaq(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenLicense -> {
        this.openSettingsLicense(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenPrivacy -> {
        this.openSettingsPrivacy(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenAcknowledgments -> {
        this.openSettingsAcknowledgements(event.title, event.url)
        state
      }
    }
  }

  private fun handleSettingsDebugEvent(
    event: SettingsDebugEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      SettingsDebugEvent.OpenCustomOPDS -> {
        this.openSettingsCustomOPDS()
        state
      }

      is SettingsDebugEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      SettingsDebugEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleSettingsDocumentViewerEvent(
    event: SettingsDocumentViewerEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      SettingsDocumentViewerEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun popBackStack() {
    this.navigator.popBackStack()
  }

  private fun openSettingsAbout(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsAccounts() {
    this.navigator.addFragment(
      fragment = AccountListFragment.create(
        AccountListFragmentParameters(
          shouldShowLibraryRegistryMenu = this.settingsConfiguration.allowAccountsRegistryAccess,
          accountID = null,
          barcode = null,
          comingFromDeepLink = false
        )
      ),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsAcknowledgements(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsEULA(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsFaq(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsLicense(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsPrivacy(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsVersion() {
    this.navigator.addFragment(
      fragment = SettingsDebugFragment(),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsCustomOPDS() {
    this.navigator.addFragment(
      fragment = SettingsCustomOPDSFragment(),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openErrorPage(parameters: ErrorPageParameters) {
    this.navigator.addFragment(
      fragment = ErrorPageFragment.create(parameters),
      tab = this.navigator.currentTab()
    )
  }

  private fun openCardCreatorWebView(parameters: AccountCardCreatorParameters) {
    this.navigator.addFragment(
      fragment = AccountCardCreatorFragment.create(parameters),
      tab = this.navigator.currentTab()
    )
  }

  private fun goUpwards() {
    this.navigator.popBackStack()
  }

  private fun openSettingsAccount(
    accountID: AccountID,
    comingFromBookLoanRequest: Boolean,
    comingFromDeepLink: Boolean,
    barcode: String?
  ) {
    this.logger.debug("openSettingsAccount called with comingFromDeepLink: $comingFromDeepLink")
    this.navigator.addFragment(
      fragment = AccountDetailFragment.create(
        AccountFragmentParameters(
          accountID = accountID,
          showPleaseLogInTitle = comingFromBookLoanRequest,
          hideToolbar = comingFromDeepLink,
          barcode = barcode
        )
      ),
      tab = this.navigator.currentTab()
    )
  }

  private fun openSAML20Login() {
    // No longer used
  }

  private fun openDocViewer(
    title: String,
    url: URL
  ) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url.toString()),
      tab = this.navigator.currentTab()
    )
  }

  private fun openBookDetail(
    feedArguments: CatalogFeedArguments,
    entry: FeedEntry.FeedEntryOPDS
  ) {
    this.navigator.addFragment(
      fragment = CatalogBookDetailFragment.create(
        CatalogBookDetailFragmentParameters(
          feedEntry = entry,
          feedArguments = feedArguments
        )
      ),
      tab = this.navigator.currentTab()
    )
  }

  private fun openFeed(feedArguments: CatalogFeedArguments) {
    this.navigator.addFragment(
      fragment = CatalogFeedFragment.create(feedArguments),
      tab = this.navigator.currentTab()
    )
  }

  private fun openAccountRegistry(tab: Int) {
    this.navigator.addFragment(
      fragment = AccountListRegistryFragment(),
      tab = tab
    )
  }

  private fun openCatalog() {
    this.navigator.popBackStack()
    this.navigator.reset(org.librarysimplified.ui.tabs.R.id.tabCatalog, false)
  }

  private fun openViewer(
    book: Book,
    format: BookFormat
  ) {
    val viewerPreferences =
      ViewerPreferences(
        flags = mapOf()
      )

    Viewers.openViewer(
      context = this.fragment.requireActivity(),
      preferences = viewerPreferences,
      book = book,
      format = format
    )
  }
}
