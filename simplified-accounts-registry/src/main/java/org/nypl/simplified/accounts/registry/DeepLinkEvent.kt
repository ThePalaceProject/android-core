package org.nypl.simplified.accounts.registry

import org.nypl.simplified.accounts.api.AccountID

/**
 * The type of deep link events.
 */
sealed class DeepLinkEvent() {

  /**
   * The account ID for the library to be navigated to.
   */

  abstract val accountID: AccountID?

  /**
   * The screen ID for the screen to be navigated to.
   */

  abstract val screenID: ScreenID

  /**
   * The barcode to populate on the library login screen. Does nothing unless screenID is ScreenID.LOGIN. (Optional)
   */

  abstract val barcode: String?

  /**
   * A new deep link was intercepted
   */

  data class DeepLinkIntercepted(
    override val accountID: AccountID,
    override val screenID: ScreenID,
    override val barcode: String?
  ) : DeepLinkEvent()
}
