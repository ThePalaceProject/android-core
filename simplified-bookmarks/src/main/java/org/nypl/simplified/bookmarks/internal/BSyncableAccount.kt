package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.database.api.AccountType
import java.net.URI

internal data class BSyncableAccount(
  val account: AccountType,
  val annotationsURI: URI,
  val credentials: AccountAuthenticationCredentials
) {

  companion object {

    /**
     * Determine if a given account supports syncing, and return a [BSyncableAccount]
     * if syncing is supported.
     */

    fun ofAccount(
      account: AccountType
    ): BSyncableAccount? {
      val credentials = account.loginState.credentials
      val annotationsURI = credentials?.annotationsURI
      return if (annotationsURI != null) {
        return BSyncableAccount(
          account = account,
          annotationsURI = annotationsURI,
          credentials = credentials
        )
      } else {
        null
      }
    }
  }
}
