package org.nypl.simplified.books.api

import android.content.Context
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.lcp.LCPContentProtectionProvider
import org.readium.r2.shared.publication.ContentProtection
import org.slf4j.LoggerFactory

object BookContentProtections {
  private val logger =
    LoggerFactory.getLogger(BookContentProtections::class.java)

  /*
   * Instantiate any content protections that might be needed for DRM...
   */

  fun create(
    context: Context,
    contentProtectionProviders: List<ContentProtectionProvider>,
    drmInfo: BookDRMInformation,
    isManualPassphraseEnabled: Boolean = false,
    onLCPDialogDismissed: () -> Unit = {}
  ): List<ContentProtection> {
    return contentProtectionProviders.mapNotNull { provider ->
      this.logger.debug("instantiating content protection provider {}", provider.javaClass.canonicalName)

      /*
       * XXX: It's unpleasant to have to special-case like this, but we don't control the
       * org.nypl.drm.core.ContentProtectionProvider interface. When LCP is implemented upstream,
       * all of those interfaces can be upgraded to properly support passing in credentials.
       */

      if (provider is LCPContentProtectionProvider) {
        when (drmInfo) {
          is BookDRMInformation.LCP -> {
            provider.passphrase = drmInfo.hashedPassphrase
            provider.isManualPassphraseEnabled = isManualPassphraseEnabled
            provider.onLcpDialogDismissed = onLCPDialogDismissed
          }
          else -> {
            // do nothing
          }
        }
      }
      provider.create(context)
    }
  }
}
