package org.nypl.simplified.ui.accounts.saml20

import org.nypl.simplified.accounts.api.AccountCookie
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription

sealed class AccountSAML20State {

  data object WebViewInitializing : AccountSAML20State()

  data object WebViewInitialized : AccountSAML20State()

  data object WebViewRequestSent : AccountSAML20State()

  data class TokenObtained(
    val accountID: AccountID,
    val token: String,
    val patronInfo: String,
    val cookies: List<AccountCookie>
  ) : AccountSAML20State()

  data class Failed(
    val accountID: AccountID,
    val description: AccountProviderAuthenticationDescription.SAML2_0,
    val message: String
  ) : AccountSAML20State()
}
