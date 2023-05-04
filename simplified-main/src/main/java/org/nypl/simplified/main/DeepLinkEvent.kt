package org.nypl.simplified.main

import org.nypl.simplified.accounts.api.AccountID

/**
   * The type of deep link events.
   */

sealed class DeepLinkEvent() {

  /**
   * The library ID to be navigated to
   */

  abstract val libraryID: AccountID

  /**
   * A new deep link was intercepted
   */

  data class DeepLinkIntercepted(
    override val libraryID: AccountID,
  ) : DeepLinkEvent()
}
