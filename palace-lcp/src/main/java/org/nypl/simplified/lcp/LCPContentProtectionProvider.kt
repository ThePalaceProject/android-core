package org.nypl.simplified.lcp

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librarysimplified.lcp.R
import org.nypl.drm.core.ContentProtectionProvider
import org.readium.r2.lcp.LcpAuthenticating
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
  private var passphrase: String? = null

  fun setPassphraseFromHashed(
    text: String?
  ) {
    if (text == null) {
      this.passphrase = null
    } else {
      this.passphrase = LCPHashedPassphrase.conditionallyBase64Decode(text)
    }
    logger.debug("setPassphraseFromHashed: {}", this.passphrase)
  }

  fun setPassphraseFromClear(
    text: String?
  ) {
    if (text == null) {
      this.passphrase = null
    } else {
      this.passphrase = text
    }
    logger.debug("setPassphraseFromClear: {}", this.passphrase)
  }

  /**
   * The action to perform when the manual passphrase dialog is dismissed.
   */
  var onLcpDialogDismissed: () -> Unit = {}

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

  private suspend fun askPassphrase(context: Context, hint: String): String? {
    val view = LayoutInflater.from(context).inflate(R.layout.view_manual_lcp_passphrase, null)
    val inputPassphrase = view.findViewById<TextView>(R.id.inputPassphrase)

    return suspendCoroutine { cont ->
      try {
        val dialogBuilder = MaterialAlertDialogBuilder(context)
        dialogBuilder.setTitle(R.string.dialog_manual_passphrase_title)
        dialogBuilder.setMessage(hint)
        dialogBuilder.setView(view)

        dialogBuilder.setPositiveButton(R.string.dialog_manual_passphrase_done) { dialog, _ ->
          dialog.dismiss()
          try {
            this.setPassphraseFromClear(inputPassphrase.text.toString().trim())
            cont.resume(this.passphrase())
          } catch (e: Throwable) {
            this.logger.debug("Dialog failure: ", e)
          }
        }

        dialogBuilder.setNegativeButton(R.string.dialog_manual_passphrase_cancel) { dialog, _ ->
          this.setPassphraseFromClear(null)
          dialog.dismiss()
        }

        dialogBuilder.setOnDismissListener {
          try {
            this.setPassphraseFromClear(null)
            cont.resume(null)
            this.onLcpDialogDismissed()
          } catch (e: Throwable) {
            this.logger.debug("Dialog failure: ", e)
          }
        }

        val dialog = dialogBuilder.create()
        dialog.show()
      } catch (e: Throwable) {
        this.logger.debug("Dialog failure: ", e)
      }
    }
  }

  override fun create(
    context: Activity
  ): ContentProtection? {
    val httpClient =
      DefaultHttpClient()

    val assetRetriever =
      AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = httpClient,
      )

    val lcpService =
      LcpService(
        context = context,
        assetRetriever = assetRetriever
      )

    return if (lcpService == null) {
      this.logger.debug("LCP service is unavailable")
      return null
    } else {
      lcpService.contentProtection(
        object : LcpAuthenticating {
          override suspend fun retrievePassphrase(
            license: LcpAuthenticating.AuthenticatedLicense,
            reason: LcpAuthenticating.AuthenticationReason,
            allowUserInteraction: Boolean
          ): String? {
            logger.debug(
              "Retrieving passphrase: {} (allowUserInteraction: {})",
              reason,
              allowUserInteraction
            )

            if (!allowUserInteraction) {
              return this@LCPContentProtectionProvider.passphrase()
            }

            return withContext(Dispatchers.Main) {
              this@LCPContentProtectionProvider.askPassphrase(context, license.hint)
            }
          }
        }
      )
    }
  }
}
