package org.nypl.simplified.ui.accounts.view_bindings

import android.view.ViewGroup
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus

class ViewsForAnonymous(
  override val viewGroup: ViewGroup
) : Base() {

  override fun lock() {
    // Nothing
  }

  override fun unlock() {
    // Nothing
  }

  override fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    // Nothing
  }

  override fun setResetPasswordLabelStatus(
    status: AccountLoginButtonStatus,
    isVisible: Boolean,
    onClick: () -> Unit
  ) {
    // Nothing
  }

  override fun clearActual() {
    // Nothing
  }

  companion object {
    fun bind(viewGroup: ViewGroup): ViewsForAnonymous {
      return ViewsForAnonymous(viewGroup)
    }
  }
}
