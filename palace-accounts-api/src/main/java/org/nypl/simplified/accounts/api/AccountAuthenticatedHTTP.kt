package org.nypl.simplified.accounts.api

import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPProblemReport
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPRequestConstants
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.Handled401.ErrorIsRecoverableCredentialsExpired
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.Handled401.ErrorIsUnrecoverable

/**
 * Convenient functions to construct authenticated HTTP instances from sets of credentials.
 */

object AccountAuthenticatedHTTP {

  fun createAuthorization(
    credentials: AccountAuthenticationCredentials
  ): LSHTTPAuthorizationType {
    return when (credentials) {
      is AccountAuthenticationCredentials.Basic ->
        LSHTTPAuthorizationBasic.ofUsernamePassword(
          userName = credentials.userName.value,
          password = credentials.password.value
        )

      is AccountAuthenticationCredentials.BasicToken ->
        if (credentials.authenticationTokenInfo.accessToken.isNotBlank()) {
          LSHTTPAuthorizationBearerToken.ofToken(
            token = credentials.authenticationTokenInfo.accessToken
          )
        } else {
          LSHTTPAuthorizationBasic.ofUsernamePassword(
            userName = credentials.userName.value,
            password = credentials.password.value
          )
        }

      is AccountAuthenticationCredentials.SAML2_0 ->
        LSHTTPAuthorizationBearerToken.ofToken(
          token = credentials.accessToken
        )
    }
  }

  fun createAuthorizationIfPresent(
    credentials: AccountAuthenticationCredentials?
  ): LSHTTPAuthorizationType? {
    return credentials?.let(this::createAuthorization)
  }

  fun LSHTTPRequestBuilderType.addBasicTokenPropertiesIfApplicable(
    credentials: AccountAuthenticationCredentials?
  ): LSHTTPRequestBuilderType {
    if (credentials !is AccountAuthenticationCredentials.BasicToken) {
      return this
    }
    return addBasicTokenProperties(credentials)
  }

  fun LSHTTPRequestBuilderType.addBasicTokenProperties(
    credentials: AccountAuthenticationCredentials.BasicToken
  ): LSHTTPRequestBuilderType {
    return apply {
      setExtensionProperty(
        LSHTTPRequestConstants.PROPERTY_KEY_USERNAME,
        credentials.userName.value
      )
      setExtensionProperty(
        LSHTTPRequestConstants.PROPERTY_KEY_PASSWORD,
        credentials.password.value
      )
      setExtensionProperty(
        LSHTTPRequestConstants.PROPERTY_KEY_AUTHENTICATION_URL,
        credentials.authenticationTokenInfo.authURI.toString()
      )
    }
  }

  fun LSHTTPResponseStatus.getAccessToken(): String? {
    return properties?.header(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN)
  }

  /**
   * A handled 401 status code.
   */

  sealed class Handled401 {
    /** A recoverable error; ask the user to log in again. */
    data object ErrorIsUnrecoverable : Handled401()

    /** An unrecoverable error; just fail. */
    data object ErrorIsRecoverableCredentialsExpired : Handled401()
  }

  /**
   * Handle a 401 error along with a possibly-not-present problem report.
   *
   * The server will return useful problem reports that indicate whether a 401 error is
   * recoverable or not. If an error _is_ recoverable, then the app should ask the user to log
   * in again. If an error isn't recoverable, then it shouldn't be treated any differently to
   * any other kind of error.
   */

  fun handle401Error(
    problemReport: LSHTTPProblemReport?
  ): Handled401 {
    return if (problemReport != null) {
      val type = problemReport.type
      if (type != null) {
        // XXX: Deprecated: The server will soon stop serving this type.
        if (type.startsWith("http://librarysimplified.org/terms/problem/")) {
          ErrorIsRecoverableCredentialsExpired
        } else if (type.startsWith("http://palaceproject.io/terms/problem/auth/recoverable")) {
          ErrorIsRecoverableCredentialsExpired
        } else {
          ErrorIsUnrecoverable
        }
      } else {
        ErrorIsUnrecoverable
      }
    } else {
      ErrorIsUnrecoverable
    }
  }
}
