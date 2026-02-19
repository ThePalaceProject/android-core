package org.librarysimplified.viewer.audiobook

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest.Kind
import org.librarysimplified.audiobook.api.PlayerDownloadRequest.Kind.CHAPTER
import org.librarysimplified.audiobook.api.PlayerDownloadRequest.Kind.WHOLE_BOOK
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAUsernamePassword
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.bearer_token.LSSimplifiedBearerTokenNegotiation
import org.librarysimplified.http.refresh_token.LSHTTPRefreshTokenProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.Basic
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.BasicToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.OAuthWithIntermediary
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials.SAML2_0
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/**
 * An authorization handler used when an audiobook is opened.
 *
 * @see "https://www.notion.so/lyrasis/How-To-Fulfill-Audiobooks-30624d4ececa80e39c7efc39f82ebe78"
 */

class AudioBookAuthorizationHandler(
  private val account: AccountType,
  private val book: Book,
  private val httpClient: LSHTTPClientType,
) : PlayerAuthorizationHandlerType {

  private val logger =
    LoggerFactory.getLogger(AudioBookAuthorizationHandler::class.java)

  private var simplifiedBearerToken: SimplifiedBearerToken? = null

  private data class SimplifiedBearerToken(
    val expires: OffsetDateTime,
    val accessToken: String
  )

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: Kind
  ) {
    this.logger.debug("onAuthorizationIsNoLongerInvalid: {} {}", source.hrefURI, kind)
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: Kind
  ) {
    this.logger.debug("onAuthorizationIsInvalid: {} {}", source.hrefURI, kind)
    this.account.expireCredentialsIfApplicable()
    this.simplifiedBearerToken = null
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: Kind
  ): LSHTTPAuthorizationType? {
    this.logger.debug("onConfigureAuthorizationFor: {} {}", source.hrefURI, kind)

    /*
     * Books that are gated by Library Simplified bearer tokens will typically require them on
     * both the manifest and on all chapters.
     */

    if (this.bookSimplifiedBearerTokenRequired()) {
      if (!this.bookSimplifiedBearerTokenValid()) {
        return this.bookSimplifiedBearerTokenRefresh()
      } else {
        val token = this.simplifiedBearerToken?.accessToken
        return token?.let { t -> LSHTTPAuthorizationBearerToken.ofToken(t) }
      }
    }

    /*
     * At the time of writing, all WHOLE_BOOK requests are made to external servers that
     * do not require authorization.
     */

    if (kind == WHOLE_BOOK) {
      return null
    }

    /*
     * Otherwise, at the time of writing, no books require explicit credentials on chapter
     * requests.
     */

    if (kind == CHAPTER) {
      return null
    }

    return AccountAuthenticatedHTTP.createAuthorizationIfPresent(this.account.loginState.credentials)
  }

  private fun bookSimplifiedBearerTokenRefresh(): LSHTTPAuthorizationType? {
    val target = this.book.entry.acquisitions[0].uri.hrefURI
    if (target == null) {
      this.logger.warn("Cannot negotiate bearer tokens from templated URIs.")
      return null
    }

    val link =
      PlayerManifestLink.LinkBasic(target)

    val refreshProperties =
      when (val c = this.account.loginState.credentials) {
        is Basic -> null
        is BasicToken ->
          LSHTTPRefreshTokenProperties(
            userName = c.userName.value,
            password = c.password.value,
            refreshURI = c.authenticationTokenInfo.authURI
          )

        is OAuthWithIntermediary -> null
        is SAML2_0 -> null
        null -> null
      }

    val tokenResult =
      LSSimplifiedBearerTokenNegotiation.negotiate(
        client = this.httpClient,
        target = target,
        refreshTokenProperties = refreshProperties,
        authorization = AccountAuthenticatedHTTP.createAuthorizationIfPresent(this.account.loginState.credentials)
      )

    return when (tokenResult) {
      is LSSimplifiedBearerTokenNegotiation.NegotiationFailed -> {
        this.logger.debug("Failed to negotiate bearer token.")
        this.onAuthorizationIsInvalid(link, CHAPTER)
        null
      }

      is LSSimplifiedBearerTokenNegotiation.NegotiationSucceeded -> {
        this.logger.debug("Negotiated new bearer token.")
        this.account.updateBasicTokenCredentials(tokenResult.refreshToken?.value)

        val expiration =
          OffsetDateTime.now().plusSeconds(tokenResult.token.expiresIn.toLong())
        this.simplifiedBearerToken =
          SimplifiedBearerToken(
            expires = expiration,
            accessToken = tokenResult.token.accessToken
          )

        this.onAuthorizationIsNoLongerInvalid(link, CHAPTER)
        LSHTTPAuthorizationBearerToken.ofToken(tokenResult.token.accessToken)
      }
    }
  }

  private fun bookSimplifiedBearerTokenValid(): Boolean {
    val existingToken = this.simplifiedBearerToken
    return if (existingToken != null) {
      val timeNow = OffsetDateTime.now()
      if (timeNow.isAfter(existingToken.expires)) {
        this.logger.debug("Bearer token appears to have expired.")
        this.simplifiedBearerToken = null
        return false
      } else {
        true
      }
    } else {
      false
    }
  }

  private fun bookSimplifiedBearerTokenRequired(): Boolean {
    return this.book.entry.acquisitions[0].type.fullType == "application/vnd.librarysimplified.bearer-token+json"
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: Kind,
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
    return when (val credentials = this.account.loginState.credentials) {
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
