package org.nypl.simplified.profiles.controller.api

import org.nypl.simplified.accounts.api.AccountCookie
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountUsername
import java.net.URI

/**
 * A request to log in to an account.
 */

sealed class ProfileAccountLoginRequest {

  /**
   * The ID of the account.
   */

  abstract val accountId: AccountID

  /**
   * A request to log in using basic authentication.
   */

  data class Basic(
    override val accountId: AccountID,
    val description: AccountProviderAuthenticationDescription.Basic,
    val username: AccountUsername,
    val password: AccountPassword
  ) : ProfileAccountLoginRequest()

  /**
   * A request to log in using basic token authentication.
   */

  data class BasicToken(
    override val accountId: AccountID,
    val description: AccountProviderAuthenticationDescription.BasicToken,
    val username: AccountUsername,
    val password: AccountPassword
  ) : ProfileAccountLoginRequest()

  /**
   * The set of requests that apply to SAML 2.0.
   */

  sealed class SAML20 : ProfileAccountLoginRequest()

  /**
   * A request to begin a login using SAML 2.0 authentication.
   */

  data class SAML20Initiate(
    override val accountId: AccountID,
    val description: AccountProviderAuthenticationDescription.SAML2_0
  ) : SAML20()

  /**
   * A request to complete a login using SAML 2.0 authentication. In other
   * words, a set of SAML information has been passed to the application.
   */

  data class SAML20Complete(
    override val accountId: AccountID,
    val accessToken: String,
    val patronInfo: String,
    val cookies: List<AccountCookie>
  ) : SAML20()

  /**
   * A request to cancel waiting for a login using SAML 2.0 authentication.
   */

  data class SAML20Cancel(
    override val accountId: AccountID,
    val description: AccountProviderAuthenticationDescription.SAML2_0
  ) : SAML20()

  /**
   * The set of requests that apply to OpenID Connect.
   */

  sealed class OIDC : ProfileAccountLoginRequest()

  /**
   * A request to begin a login using OpenID Connect authentication.
   */

  data class OIDCInitiate(
    override val accountId: AccountID,
    val description: AccountProviderAuthenticationDescription.OpenIDConnect,
    val redirectURI: URI,
  ) : OIDC()

  /**
   * A request to complete a login using OpenID Connect authentication. In other
   * words, a set of SAML information has been passed to the application.
   */

  data class OIDCComplete(
    override val accountId: AccountID,
    val accessToken: String,
  ) : OIDC()

  /**
   * A request to cancel waiting for a login using OpenID Connect authentication.
   */

  data class OIDCCancel(
    override val accountId: AccountID,
    val description: AccountProviderAuthenticationDescription.OpenIDConnect
  ) : OIDC()
}
