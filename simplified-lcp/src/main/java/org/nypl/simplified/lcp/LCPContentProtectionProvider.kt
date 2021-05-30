package org.nypl.simplified.lcp

import android.content.Context
import org.nypl.drm.core.ContentProtectionProvider
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.publication.ContentProtection
import org.slf4j.LoggerFactory

/**
 * A content protection provider for LCP.
 *
 * Note: This class *must* have a zero-arg public constructor in order to be used via ServiceLoader.
 */

class LCPContentProtectionProvider : ContentProtectionProvider {

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
      lcpService.contentProtection()
    }
  }
}
