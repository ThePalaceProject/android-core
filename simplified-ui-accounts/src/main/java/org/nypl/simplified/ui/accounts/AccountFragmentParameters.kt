package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountID
import java.io.Serializable

/**
 * Parameters for the account screen.
 */

data class AccountFragmentParameters(

  /**
   * The account that will be displayed.
   */

  val accountID: AccountID,

  /**
   * Show the "please log in to continue" title.
   */

  val showPleaseLogInTitle: Boolean,

  /**
   * Hide the toolbar and back button.
   */

  val hideToolbar: Boolean,

  /**
   * Pre-populate the barcode with the provided value (e.g. from deep links).
   */

  val barcode: String?

) : Serializable
