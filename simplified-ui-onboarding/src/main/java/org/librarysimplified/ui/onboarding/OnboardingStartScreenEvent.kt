package org.librarysimplified.ui.onboarding

sealed class OnboardingStartScreenEvent {

  object FindLibrary : OnboardingStartScreenEvent()

  object AddLibraryLater : OnboardingStartScreenEvent()
}
