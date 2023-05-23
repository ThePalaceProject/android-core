package org.nypl.simplified.accounts.registry

import org.nypl.simplified.accounts.api.AccountID

/**
 * The type of deep link events.
 */
sealed class DeepLinkEvent() {

  /**
   * The account ID for the library login screen to be navigated to.
   */

  abstract val accountID: AccountID

  /**
   * The barcode to populate on the library login screen. (Optional)
   */

  abstract val barcode: String?

  /**
   * A new deep link was intercepted
   */

  data class DeepLinkIntercepted(
    override val accountID: AccountID,
    override val barcode: String?
  ) : DeepLinkEvent()
}
