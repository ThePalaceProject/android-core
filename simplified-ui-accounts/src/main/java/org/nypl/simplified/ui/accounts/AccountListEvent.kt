package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.ui.errorpage.ErrorPageParameters

sealed class AccountListEvent {

  /**
   * An existing account has been selected.
   */

  data class AccountSelected(
    val accountID: AccountID,
    val barcode: String?,
    val comingFromDeepLink: Boolean
  ) : AccountListEvent()

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : AccountListEvent()

  /**
   * The patron wants to add a new account.
   */

  object AddAccount : AccountListEvent()

  /**
   * The patron doesn't want to look at the account list anymore.
   */

  object GoUpwards : AccountListEvent()
}
