package org.librarysimplified.main

sealed class MainActivityListenedEvent {

  data class SplashEvent(
    val event: org.librarysimplified.ui.splash.SplashEvent
  ) : MainActivityListenedEvent()

  data class TutorialEvent(
    val event: org.librarysimplified.ui.tutorial.TutorialEvent
  ) : MainActivityListenedEvent()

  data class OnboardingEvent(
    val event: org.librarysimplified.ui.onboarding.OnboardingEvent
  ) : MainActivityListenedEvent()
}
