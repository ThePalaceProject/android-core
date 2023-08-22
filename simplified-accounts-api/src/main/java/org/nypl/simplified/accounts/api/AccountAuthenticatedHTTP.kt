package org.nypl.simplified.accounts.api

import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPRequestConstants
import org.librarysimplified.http.api.LSHTTPResponseStatus

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
      is AccountAuthenticationCredentials.OAuthWithIntermediary ->
        LSHTTPAuthorizationBearerToken.ofToken(
          token = credentials.accessToken
        )
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

  fun LSHTTPRequestBuilderType.addCredentialsToProperties(
    credentials: AccountAuthenticationCredentials?
  ): LSHTTPRequestBuilderType {
    if (credentials !is AccountAuthenticationCredentials.BasicToken) {
      return this
    }

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

  fun LSHTTPResponseStatus.getUpdatedLoginState(
    currentLoginState: AccountLoginState,
    credentials: AccountAuthenticationCredentials?
  ): AccountLoginState {
    if (credentials !is AccountAuthenticationCredentials.BasicToken ||
      currentLoginState !is AccountLoginState.AccountLoggedIn
    ) {
      return currentLoginState
    }

    val newAccessToken = getAccessToken()

    return if (!newAccessToken.isNullOrBlank()) {
      AccountLoginState.AccountLoggedIn(
        credentials = credentials.copy(
          authenticationTokenInfo = credentials.authenticationTokenInfo.copy(
            accessToken = newAccessToken
          )
        )
      )
    } else {
      currentLoginState
    }
  }
}
