package org.librarysimplified.main

sealed class MainActivityListenedEvent {

  data class SplashEvent(
    val event: org.nypl.simplified.ui.splash.SplashEvent
  ) : MainActivityListenedEvent()

  data class TutorialEvent(
    val event: org.nypl.simplified.ui.tutorial.TutorialEvent
  ) : MainActivityListenedEvent()

  data class OnboardingEvent(
    val event: org.nypl.simplified.ui.onboarding.OnboardingEvent
  ) : MainActivityListenedEvent()
}
