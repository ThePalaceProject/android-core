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

  val accountId: AccountID,

  /**
   * Show the "please log in to continue" title.
   */

  val showPleaseLogInTitle: Boolean

) : Serializable
