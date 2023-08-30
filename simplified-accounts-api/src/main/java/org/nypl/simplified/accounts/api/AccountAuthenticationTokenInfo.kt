package org.nypl.simplified.accounts.api

import java.net.URI

data class AccountAuthenticationTokenInfo(
  val accessToken: String,
  val authURI: URI
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AccountAuthenticationTokenInfo

    if (authURI != other.authURI) return false

    return true
  }

  override fun hashCode(): Int {
    return authURI.hashCode()
  }
}
