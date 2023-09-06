package org.librarysimplified.ui.onboarding

sealed class OnboardingEvent {

  object OnboardingCompleted : OnboardingEvent()
}
