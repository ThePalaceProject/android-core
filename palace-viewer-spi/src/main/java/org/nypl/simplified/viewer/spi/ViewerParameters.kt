package org.nypl.simplified.viewer.spi

import org.nypl.simplified.accounts.api.AccountID

/**
 * Parameters for viewers. These parameters can influence which viewers will be selected
 * for a given book, and can also allow for limited callbacks into the application from each
 * viewer.
 */

data class ViewerParameters(

  /**
   * An opaque, implementation-defined set of flags.
   */

  val flags: Map<String, Boolean>,

  /**
   * A viewer wants to open the login screen.
   */

  val onLoginRequested: (AccountID) -> Unit
)
