package org.nypl.simplified.books.api

import android.app.Application
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.lcp.LCPContentProtectionProvider
import org.readium.r2.shared.publication.protection.ContentProtection
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

object BookContentProtections {

  private val logger =
    LoggerFactory.getLogger(BookContentProtections::class.java)

  /*
   * Instantiate any content protections that might be needed for DRM...
   *
   * XXX: This interface needs to be replaced with something else. It's messy and is trying to
   * squash multiple different interfaces into a single method.
   */

  fun create(
    context: Application,
    contentProtectionProviders: List<ContentProtectionProvider>,
    boundless: BoundlessServiceType?,
    format: BookFormat,
    drmInfo: BookDRMInformation,
    isManualPassphraseEnabled: Boolean = false,
    onLCPDialogDismissed: () -> Unit = {}
  ): List<ContentProtection> {
    return try {
      when (drmInfo) {
        /*
         * ACS doesn't require any special handling. Just instantiate the one provider if it exists.
         */

        is BookDRMInformation.ACS -> {
          contentProtectionProviders.mapNotNull { provider -> provider.create(context) }
        }

        /*
         * Boundless only works for EPUB files, and requires a bit of information up-front.
         */
        is BookDRMInformation.Boundless -> {
          if (boundless != null) {
            if (format is BookFormat.BookFormatEPUB) {
              listOf(
                boundless.createContentProtection(
                  epubFile = format.file!!,
                  licenseFile = drmInfo.license!!,
                  tempDirectory = context.cacheDir,
                  inMemorySizeThreshold = 10_000_000UL,
                  currentTime = OffsetDateTime.now()
                )
              )
            } else {
              listOf()
            }
          } else {
            listOf()
          }
        }

        /*
         * LCP requires data up front.
         */
        is BookDRMInformation.LCP -> {
          val lcpProvider =
            contentProtectionProviders.filter { provider -> provider is LCPContentProtectionProvider }
              .map { provider -> provider as LCPContentProtectionProvider }
              .firstOrNull()

          if (lcpProvider != null) {
            lcpProvider.passphrase = drmInfo.hashedPassphrase
            lcpProvider.isManualPassphraseEnabled = isManualPassphraseEnabled
            lcpProvider.onLcpDialogDismissed = onLCPDialogDismissed
            listOfNotNull(lcpProvider.create(context))
          } else {
            listOf()
          }
        }

        BookDRMInformation.None -> {
          listOf()
        }
      }
    } catch (e: Throwable) {
      this.logger.error("Failed to handle DRM providers: ", e)
      listOf()
    }
  }
}
