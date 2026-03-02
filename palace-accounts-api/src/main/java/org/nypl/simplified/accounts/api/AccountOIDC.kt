package org.nypl.simplified.accounts.api

import android.content.Intent
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import java.util.regex.Pattern

/**
 * Functions and values for OIDC.
 */

object AccountOIDC {

  private val logger =
    LoggerFactory.getLogger(AccountOIDC::class.java)

  /**
   * The OIDC callback URI scheme.
   */

  val oidcCallbackScheme =
    "palace-oidc-callback"

  /**
   * The OIDC callback host.
   */

  val oidcCallbackHost =
    "org.thepalaceproject.oidc"

  /**
   * The OIDC callback URI for a given account.
   */

  fun oidcCallbackURI(
    account: AccountID
  ): URI {
    return URI.create("${this.oidcCallbackScheme}://${this.oidcCallbackHost}/account/${account.uuid}/")
  }

  /**
   * Check if an intent is an OIDC callback intent.
   */

  fun isIntentOIDC(
    intent: Intent
  ): Boolean {
    if (intent.action != Intent.ACTION_VIEW) {
      this.logger.warn("Received an intent with action ${intent.action}. Ignoring it!")
      return false
    }
    val data = intent.data
    if (data == null) {
      this.logger.warn("Received an intent no data. Ignoring it!")
      return false
    }

    this.logger.debug("Received an intent with data: {}", data)
    if (data.scheme != this.oidcCallbackScheme) {
      this.logger.warn("Received an intent with scheme ${data.scheme}. Ignoring it!")
      return false
    }
    if (data.host != this.oidcCallbackHost) {
      this.logger.warn("Received an intent with host ${data.host}. Ignoring it!")
      return false
    }
    return true
  }

  private val pathAccountPattern =
    Pattern.compile("^/account/([0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12})/$")

  fun parseOIDCCallback(
    data: URI
  ): AccountOIDCParsedCallback {
    val path = data.path
    val matcher = pathAccountPattern.matcher(path)
    if (!matcher.matches()) {
      throw IllegalArgumentException("Unparseable intent data: $data")
    }

    val accountIDText =
      matcher.group(1)
    val accountID =
      AccountID(UUID.fromString(accountIDText))

    val queryRaw =
      data.query ?: throw IllegalArgumentException("Missing query parameters: $data")
    val queryParameters =
      queryRaw.split('&')

    var accessToken: String? = null
    for (parameter in queryParameters) {
      val keyValue = parameter.split('=')
      val key = keyValue[0]
      val value = keyValue[1]

      if (key == "access_token") {
        accessToken = value.trim()
      }
    }

    if (accessToken == null) {
      throw IllegalArgumentException("Failed to locate an access_token parameter: $data")
    }

    return AccountOIDCParsedCallback(
      account = accountID,
      accessToken = accessToken
    )
  }

  data class AccountOIDCParsedCallback(
    val account: AccountID,
    val accessToken: String
  )
}
