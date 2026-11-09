package org.nypl.simplified.accounts.json.internal

import com.fasterxml.jackson.databind.node.ObjectNode
import com.io7m.verona.core.VersionException
import com.io7m.verona.core.VersionParser
import org.nypl.drm.core.AdobeDeviceID
import org.nypl.drm.core.AdobeUserID
import org.nypl.drm.core.AdobeVendorID
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePostActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.json.core.JSONParserUtilities
import org.slf4j.LoggerFactory

object AccountAuthenticationCredentialsAdobeJSON {
  private val logger =
    LoggerFactory.getLogger(AccountAuthenticationCredentialsAdobeJSON::class.java)

  fun deserializeAdobeCredentials(credsObj: ObjectNode): AccountAuthenticationAdobePreActivationCredentials {
    val activation =
      JSONParserUtilities.getObjectOrNull(credsObj, "activation")

    val credsPost: AccountAuthenticationAdobePostActivationCredentials? =
      if (activation != null) {
        val versionString =
          JSONParserUtilities.getStringOrNull(activation, "version")
        val version =
          if (versionString == null) {
            null
          } else {
            try {
              VersionParser.parse(versionString)
            } catch (e: VersionException) {
              this.logger.debug("could not parse the ADEPT provider version: ", e)
              null
            }
          }

        AccountAuthenticationAdobePostActivationCredentials(
          deviceID = AdobeDeviceID(JSONParserUtilities.getString(activation, "device_id")),
          userID = AdobeUserID(JSONParserUtilities.getString(activation, "user_id")),
          version = version
        )
      } else {
        null
      }

    return AccountAuthenticationAdobePreActivationCredentials(
      AdobeVendorID(
        JSONParserUtilities.getString(credsObj, "vendor_id")
      ),
      AccountAuthenticationAdobeClientToken.parse(
        JSONParserUtilities.getString(credsObj, "client_token")
      ),
      JSONParserUtilities.getURIOrNull(credsObj, "device_manager_uri"),
      credsPost
    )
  }
}
