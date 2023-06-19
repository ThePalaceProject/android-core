package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountID
import java.io.Serializable

/**
 * Parameters for the accounts screen.
 */

data class AccountListFragmentParameters(

  /**
   * If set to `true`, then show the library registry menu in the toolbar.
   */

  val shouldShowLibraryRegistryMenu: Boolean,

  /**
   * The accountID to be navigated to.
   */

  val accountID: AccountID?,

  /**
   * The barcode to pre-populate on the library login screen.
   */

  val barcode: String?,

  /**
   * If set to true, then handle as originating from an intercepted deep link.
   */

  val comingFromDeepLink: Boolean?


) : Serializable
