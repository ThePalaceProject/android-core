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
   * The passphrase that will be used to open the next book.
   *
   * XXX: This kind of back-door access is required because we can't yet change the
   *      `org.nypl.drm.core.ContentProtectionProvider` interface.
   */

  @Volatile
  var passphrase: String? = null

  /**
   * @return The passphrase that will be used to open the next book.
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
