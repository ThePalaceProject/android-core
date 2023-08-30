package org.nypl.simplified.ui.accounts.view_bindings

import android.view.ViewGroup
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus

/**
 * A type representing a set of bound views for each possible type of authentication.
 */

sealed class AccountAuthenticationViewBindings {

  /**
   * The view group representing the root of the view hierarchy for the given views.
   */

  abstract val viewGroup: ViewGroup

  /**
   * "Lock" the form views, preventing the user from interacting with them.
   */

  abstract fun lock()

  /**
   * "Unlock" the form views, allowing the user to interact with them.
   */

  abstract fun unlock()

  /**
   * Clear the views. This method should be called exactly once, and all other methods
   * will raise exceptions after this method has been called.
   */

  abstract fun clear()

  /**
   * Set the status of any relevant login button.
   */

  abstract fun setLoginButtonStatus(status: AccountLoginButtonStatus)

  abstract fun setResetPasswordLabelStatus(
    status: AccountLoginButtonStatus,
    isVisible: Boolean,
    onClick: () -> Unit
  )
}
