package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.ui.errorpage.ErrorPageParameters

sealed class AccountListRegistryEvent {

  data class AccountCreated(
    val accountID: AccountID,
    val isDeepLink: Boolean
  ) : AccountListRegistryEvent()

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : AccountListRegistryEvent()

  object GoUpwards : AccountListRegistryEvent()
}
