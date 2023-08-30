package org.librarysimplified.main

import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.listeners.api.ListenerRepositoryFactory
import org.nypl.simplified.listeners.api.ListenerRepository
import org.librarysimplified.ui.onboarding.OnboardingEvent
import org.librarysimplified.ui.splash.SplashEvent
import org.librarysimplified.ui.tutorial.TutorialEvent

class MainActivityDefaultViewModelFactory(fallbackFactory: ViewModelProvider.Factory) :
  ListenerRepositoryFactory<MainActivityListenedEvent, Unit>(fallbackFactory) {

  override val initialState: Unit = Unit

  override fun onListenerRepositoryCreated(repository: ListenerRepository<MainActivityListenedEvent, Unit>) {
    repository.registerListener(SplashEvent::class, MainActivityListenedEvent::SplashEvent)
    repository.registerListener(OnboardingEvent::class, MainActivityListenedEvent::OnboardingEvent)
    repository.registerListener(TutorialEvent::class, MainActivityListenedEvent::TutorialEvent)
  }
}
