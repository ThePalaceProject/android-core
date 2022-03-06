package org.nypl.simplified.lcp

import android.content.Context
import org.nypl.drm.core.ContentProtectionProvider
import org.readium.r2.lcp.LcpAuthenticating
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.publication.ContentProtection
import org.slf4j.LoggerFactory

/**
 * A content protection provider for LCP.
 *
 * Note: This class *must* have a zero-arg public constructor in order to be used via ServiceLoader.
 */

class LCPContentProtectionProvider : ContentProtectionProvider {

  /**
   * The hashed passphrase that will be used to open the next book. The value may be set to a hex
   * string, or to its base-64 encoding, according to the spec:
   * https://readium.org/lcp-specs/notes/lcp-key-retrieval.html#sample-of-readium-web-publication-manifest-supporting-a-link-to-an-lcp-license-and-an-lcp_hashed_passphrase-property
   *
   * Note: This file uses the terms "passphrase" and "hashed passphrase" interchangeably, in all
   * cases referring to what the LCP spec calls a "hashed passphrase" or "User Key".
   *
   * XXX: This kind of back-door access is required because we can't yet change the
   *      `org.nypl.drm.core.ContentProtectionProvider` interface.
   */

  @Volatile
  var passphrase: String? = null
    set(value) {
      field = value?.let { LCPHashedPassphrase.conditionallyBase64Decode(it) }
    }

  /**
   * @return The hashed passphrase that will be used to open the next book, as a hex string (not
   * base-64 encoded).
   */

  fun passphrase(): String {
    return this.passphrase
      ?: throw IllegalStateException(
        "Please provide a passphrase to the LCPContentProtectionProvider before use!"
      )
  }

  private val logger =
    LoggerFactory.getLogger(LCPContentProtectionProvider::class.java)

  override fun create(
    context: Context
  ): ContentProtection? {
    val lcpService = LcpService(context)
    return if (lcpService == null) {
      this.logger.debug("LCP service is unavailable")
      return null
    } else {
      lcpService.contentProtection(object : LcpAuthenticating {
        override suspend fun retrievePassphrase(
          license: LcpAuthenticating.AuthenticatedLicense,
          reason: LcpAuthenticating.AuthenticationReason,
          allowUserInteraction: Boolean,
          sender: Any?
        ): String {
          return this@LCPContentProtectionProvider.passphrase()
        }
      })
    }
  }
}
