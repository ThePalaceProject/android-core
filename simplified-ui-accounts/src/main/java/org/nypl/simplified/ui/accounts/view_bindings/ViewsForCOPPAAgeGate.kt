package org.nypl.simplified.ui.accounts.view_bindings

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus
import org.nypl.simplified.ui.accounts.R

class ViewsForCOPPAAgeGate(
  override val viewGroup: ViewGroup,
  val over13: SwitchCompat
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
    this.over13.setOnClickListener {}
  }

  fun setState(
    isOver13: Boolean,
    onAgeCheckboxClicked: (View) -> Unit
  ) {
    this.over13.setOnClickListener {}
    this.over13.isChecked = isOver13
    this.over13.setOnClickListener(onAgeCheckboxClicked)
    this.over13.isEnabled = true
  }

  companion object {
    fun bind(viewGroup: ViewGroup): ViewsForCOPPAAgeGate {
      return ViewsForCOPPAAgeGate(
        viewGroup = viewGroup,
        over13 = viewGroup.findViewById(R.id.authCOPPASwitch)
      )
    }
  }
}