package org.nypl.simplified.accounts.api

import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.nypl.simplified.taskrecorder.api.TaskResult

/**
 * The current state of an account with respect to logging in/out.
 */

sealed class AccountLoginState {

  /**
   * The previous credentials, if credentials are being refreshed.
   */

  abstract val previousCredentials: AccountAuthenticationCredentials?

  /**
   * The current assumed-valid credentials.
   */

  abstract val credentials: AccountAuthenticationCredentials?

  /**
   * The account is not logged in.
   */

  data class AccountNotLoggedIn(
    override val previousCredentials: AccountAuthenticationCredentials?
  ) : AccountLoginState() {
    override val credentials: AccountAuthenticationCredentials?
      get() = this.previousCredentials

    override fun toString(): String =
      this.javaClass.simpleName
  }

  /**
   * The account is currently logging in.
   */

  data class AccountLoggingIn(
    override val previousCredentials: AccountAuthenticationCredentials?,

    /**
     * A humanly-readable status message.
     *
     * @see AccountLoginStringResourcesType
     */

    val status: String,

    /**
     * The description being used to log in.
     */

    val description: AccountProviderAuthenticationDescription,

    /**
     * `true` if the login operation can be cancelled.
     */

    val cancellable: Boolean
  ) : AccountLoginState() {
    override val credentials: AccountAuthenticationCredentials?
      get() = this.previousCredentials
  }

  /**
   * The account is currently waiting for an external authentication mechanism to complete.
   */

  data class AccountLoggingInWaitingForExternalAuthentication(
    /**
     * The previous credentials, if credentials are being refreshed.
     */

    override val previousCredentials: AccountAuthenticationCredentials?,

    /**
     * The description being used to log in.
     */

    val description: AccountProviderAuthenticationDescription,

    /**
     * A humanly-readable status message.
     *
     * @see AccountLoginStringResourcesType
     */

    val status: String
  ) : AccountLoginState() {
    override val credentials: AccountAuthenticationCredentials?
      get() = this.previousCredentials
  }

  /**
   * The account failed to log in.
   */

  data class AccountLoginFailed(
    /**
     * The previous credentials, if credentials are being refreshed.
     */

    override val previousCredentials: AccountAuthenticationCredentials?,
    val taskResult: TaskResult.Failure<*>
  ) : AccountLoginState(), PresentableErrorType {
    override val message: String =
      this.taskResult.message
    override val exception: Throwable? =
      this.taskResult.exception
    override val attributes: Map<String, String> =
      this.taskResult.attributes

    override val credentials: AccountAuthenticationCredentials?
      get() = this.previousCredentials
  }

  /**
   * The account is currently logged in.
   */

  data class AccountLoggedIn(
    override val credentials: AccountAuthenticationCredentials
  ) : AccountLoginState() {
    override val previousCredentials: AccountAuthenticationCredentials =
      this.credentials
  }

  /**
   * The account is currently logged in but the credentials appear to be stale. This can be due
   * to, for example, expired SAML or OIDC sessions.
   */

  data class AccountLoggedInStaleCredentials(
    override val credentials: AccountAuthenticationCredentials
  ) : AccountLoginState() {
    override val previousCredentials: AccountAuthenticationCredentials =
      this.credentials
  }

  /**
   * The account is currently logging out.
   */

  data class AccountLoggingOut(
    override val credentials: AccountAuthenticationCredentials,

    /**
     * A humanly-readable status message.
     *
     * @see AccountLogoutStringResourcesType
     */

    val status: String
  ) : AccountLoginState() {
    override val previousCredentials: AccountAuthenticationCredentials =
      this.credentials
  }

  /**
   * The account failed to log out
   */

  data class AccountLogoutFailed(
    val taskResult: TaskResult.Failure<*>,
    override val credentials: AccountAuthenticationCredentials
  ) : AccountLoginState(), PresentableErrorType {
    override val previousCredentials: AccountAuthenticationCredentials =
      this.credentials
    override val message: String =
      this.taskResult.message
    override val exception: Throwable? =
      this.taskResult.exception
    override val attributes: Map<String, String> =
      this.taskResult.attributes
  }
}
