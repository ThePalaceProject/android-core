package org.nypl.simplified.books.borrowing.internal

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAUsernamePassword
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.Basic
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.BasicToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.OAuthWithIntermediary
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.SAML2_0
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.borrowing.BorrowSubtaskCredentials
import org.slf4j.LoggerFactory

class BorrowAudiobookAuthorizationHandler(
  private val account: AccountType
) : PlayerAuthorizationHandlerType {

  private val logger =
    LoggerFactory.getLogger(BorrowAudiobookAuthorizationHandler::class.java)

  var subtaskCredentials: BorrowSubtaskCredentials =
    BorrowSubtaskCredentials.UseAccountCredentials

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    // Nothing to do here.
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    // Nothing to do here.
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType? {
    this.logger.debug("Configuring authorization for {}: {}", kind, source.hrefURI)

    return when (val credentials = this.subtaskCredentials) {
      BorrowSubtaskCredentials.UseAccountCredentials -> {
        AccountAuthenticatedHTTP.createAuthorizationIfPresent(this.account.loginState.credentials)
      }

      is BorrowSubtaskCredentials.UseBearerToken -> {
        LSHTTPAuthorizationBearerToken.ofToken(credentials.token)
      }
    }
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: PlayerDownloadRequest.Kind,
    credentialsType: Class<T>
  ): T {
    this.logger.debug(
      "Custom credentials required for {}: {}, type {}",
      providerName,
      kind,
      credentialsType
    )

    if (credentialsType == OPAUsernamePassword::class.java) {
      return credentialsType.cast(this.overdriveCredentialsFor())
    }

    throw UnsupportedOperationException("No available credentials of type $credentialsType.")
  }

  private fun overdriveCredentialsFor(): OPAUsernamePassword {
    return when (this.subtaskCredentials) {
      BorrowSubtaskCredentials.UseAccountCredentials -> {
        when (val credentials = this.account.loginState.credentials) {
          is Basic -> {
            OPAUsernamePassword(
              credentials.userName.value,
              this.overdrivePasswordOf(credentials.password.value)
            )
          }

          is BasicToken -> {
            OPAUsernamePassword(
              credentials.userName.value,
              this.overdrivePasswordOf(credentials.password.value)
            )
          }

          is OAuthWithIntermediary -> {
            throw UnsupportedOperationException("Overdrive audio books cannot use OAuth.")
          }

          is SAML2_0 -> {
            throw UnsupportedOperationException("Overdrive audio books cannot use SAML.")
          }

          null -> {
            throw UnsupportedOperationException("Overdrive audio books require credentials.")
          }
        }
      }

      is BorrowSubtaskCredentials.UseBearerToken -> {
        throw UnsupportedOperationException("Overdrive audio books cannot use bearer tokens.")
      }
    }
  }

  private fun overdrivePasswordOf(
    text: String
  ): OPAPassword {
    return if (text.isBlank()) {
      OPAPassword.NotRequired
    } else {
      OPAPassword.Password(text)
    }
  }
}
