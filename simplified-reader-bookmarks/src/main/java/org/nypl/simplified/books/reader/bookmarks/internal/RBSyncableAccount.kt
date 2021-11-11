package org.nypl.simplified.books.reader.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.database.api.AccountType
import java.net.URI

internal data class RBSyncableAccount(
  val account: AccountType,
  val settingsURI: URI,
  val annotationsURI: URI,
  val credentials: AccountAuthenticationCredentials
) {

  companion object {

    /**
     * Determine if a given account supports syncing, and return a [RBSyncableAccount]
     * if syncing is supported.
     */

    fun ofAccount(
      account: AccountType
    ): RBSyncableAccount? {
      val annotationsURI = account.loginState.credentials?.annotationsURI

      return if (annotationsURI != null) {
        val settingsOpt = account.provider.patronSettingsURI
        val credentials = account.loginState.credentials
        if (credentials != null && settingsOpt != null) {
          return RBSyncableAccount(
            account = account,
            settingsURI = settingsOpt,
            annotationsURI = annotationsURI,
            credentials = credentials
          )
        } else {
          null
        }
      } else {
        null
      }
    }
  }
}
