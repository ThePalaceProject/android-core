package org.nypl.simplified.ui.accounts

/**
 * The status of a logout button.
 */

sealed class AccountLogoutButtonStatus {

  /**
   * The logout button should be displayed as a "Log Out" button, should be enabled, and should
   * execute [onClick] when clicked.
   */

  data class AsLogoutButtonEnabled(
    val onClick: () -> Unit
  ) : AccountLogoutButtonStatus()

  /**
   * The logout button should be displayed as a "Log Out" button, and should be disabled.
   */

  object AsLogoutButtonDisabled : AccountLogoutButtonStatus()

  /**
   * The logout button should be displayed as a "Cancel" button, should be enabled, and should
   * execute [onClick] when clicked.
   */

  data class AsCancelButtonEnabled(
    val onClick: () -> Unit
  ) : AccountLogoutButtonStatus()

  /**
   * The logout button should be displayed as a "Cancel" button, and should be disabled.
   */

  object AsCancelButtonDisabled : AccountLogoutButtonStatus()

  /**
   * The logout button should not be displayed.
   */

  object AsButtonGone : AccountLogoutButtonStatus()
}
