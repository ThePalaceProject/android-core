package org.librarysimplified.ui.onboarding

sealed class OnboardingListenedEvent {

  data class OnboardingStartScreenEvent(
    val event: org.librarysimplified.ui.onboarding.OnboardingStartScreenEvent
  ) : OnboardingListenedEvent()

  data class AccountListRegistryEvent(
    val event: org.nypl.simplified.ui.accounts.AccountListRegistryEvent
  ) : OnboardingListenedEvent()
}
