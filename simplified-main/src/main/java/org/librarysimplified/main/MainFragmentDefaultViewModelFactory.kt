package org.librarysimplified.main

import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.listeners.api.ListenerRepositoryFactory
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.nypl.simplified.ui.accounts.AccountListEvent
import org.nypl.simplified.ui.accounts.AccountListRegistryEvent
import org.nypl.simplified.ui.accounts.AccountPickerEvent
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20Event
import org.librarysimplified.ui.catalog.CatalogBookDetailEvent
import org.librarysimplified.ui.catalog.CatalogFeedEvent
import org.librarysimplified.ui.catalog.saml20.CatalogSAML20Event
import org.nypl.simplified.ui.errorpage.ErrorPageEvent
import org.nypl.simplified.ui.settings.SettingsDebugEvent
import org.nypl.simplified.ui.settings.SettingsDocumentViewerEvent
import org.nypl.simplified.ui.settings.SettingsMainEvent

class MainFragmentDefaultViewModelFactory(fallbackFactory: ViewModelProvider.Factory) :
  ListenerRepositoryFactory<MainFragmentListenedEvent, MainFragmentState>(fallbackFactory) {

  override val initialState: MainFragmentState = MainFragmentState.EmptyState

  override fun onListenerRepositoryCreated(repository: ListenerRepository<MainFragmentListenedEvent, MainFragmentState>) {
    repository.registerListener(CatalogSAML20Event::class, MainFragmentListenedEvent::CatalogSAML20Event)
    repository.registerListener(CatalogFeedEvent::class, MainFragmentListenedEvent::CatalogFeedEvent)
    repository.registerListener(CatalogBookDetailEvent::class, MainFragmentListenedEvent::CatalogBookDetailEvent)
    repository.registerListener(AccountSAML20Event::class, MainFragmentListenedEvent::AccountSAML20Event)
    repository.registerListener(AccountDetailEvent::class, MainFragmentListenedEvent::AccountDetailEvent)
    repository.registerListener(AccountListRegistryEvent::class, MainFragmentListenedEvent::AccountListRegistryEvent)
    repository.registerListener(AccountListEvent::class, MainFragmentListenedEvent::AccountListEvent)
    repository.registerListener(AccountPickerEvent::class, MainFragmentListenedEvent::AccountPickerEvent)
    repository.registerListener(ErrorPageEvent::class, MainFragmentListenedEvent::ErrorPageEvent)
    repository.registerListener(SettingsMainEvent::class, MainFragmentListenedEvent::SettingsMainEvent)
    repository.registerListener(SettingsDebugEvent::class, MainFragmentListenedEvent::SettingsDebugEvent)
    repository.registerListener(SettingsDocumentViewerEvent::class, MainFragmentListenedEvent::SettingsDocumentViewerEvent)
  }
}
