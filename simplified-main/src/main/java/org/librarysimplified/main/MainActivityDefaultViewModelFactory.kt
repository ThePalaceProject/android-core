package org.librarysimplified.main

import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.ui.onboarding.OnboardingEvent
import org.nypl.simplified.ui.splash.SplashEvent
import org.nypl.simplified.ui.tutorial.TutorialEvent
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.ListenerRepositoryFactory

class MainActivityDefaultViewModelFactory(fallbackFactory: ViewModelProvider.Factory) :
  ListenerRepositoryFactory<MainActivityListenedEvent, Unit>(fallbackFactory) {

  override val initialState: Unit = Unit

  override fun onListenerRepositoryCreated(repository: ListenerRepository<MainActivityListenedEvent, Unit>) {
    repository.registerListener(SplashEvent::class, MainActivityListenedEvent::SplashEvent)
    repository.registerListener(OnboardingEvent::class, MainActivityListenedEvent::OnboardingEvent)
    repository.registerListener(TutorialEvent::class, MainActivityListenedEvent::TutorialEvent)
  }
}
