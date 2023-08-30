package org.nypl.simplified.ui.accounts.view_bindings

import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus
import org.librarysimplified.ui.accounts.R

class ViewsForSAML20(
  override val viewGroup: ViewGroup,
  private val loginButton: Button,
  private val resetPasswordLabel: TextView
) : Base() {

  private var loginText =
    this.viewGroup.resources.getString(R.string.accountLogin)
  private val logoutText =
    this.viewGroup.resources.getString(R.string.accountLogout)
  private val cancelText =
    this.viewGroup.resources.getString(R.string.accountCancel)

  override fun lock() {
    // Nothing
  }

  override fun unlock() {
    // Nothing
  }

  override fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    return when (status) {
      is AccountLoginButtonStatus.AsLoginButtonEnabled -> {
        this.loginButton.isEnabled = true
        this.loginButton.text = this.loginText
        this.loginButton.setOnClickListener { status.onClick.invoke() }
      }
      AccountLoginButtonStatus.AsLoginButtonDisabled -> {
        this.loginButton.isEnabled = false
        this.loginButton.text = this.loginText
      }
      is AccountLoginButtonStatus.AsCancelButtonEnabled -> {
        this.loginButton.isEnabled = true
        this.loginButton.text = this.cancelText
        this.loginButton.setOnClickListener { status.onClick.invoke() }
      }
      is AccountLoginButtonStatus.AsLogoutButtonEnabled -> {
        this.loginButton.isEnabled = true
        this.loginButton.text = this.logoutText
        this.loginButton.setOnClickListener { status.onClick.invoke() }
      }
      AccountLoginButtonStatus.AsLogoutButtonDisabled -> {
        this.loginButton.isEnabled = false
        this.loginButton.text = this.logoutText
      }
      AccountLoginButtonStatus.AsCancelButtonDisabled -> {
        this.loginButton.isEnabled = false
        this.loginButton.text = this.cancelText
      }
    }
  }

  override fun setResetPasswordLabelStatus(
    status: AccountLoginButtonStatus,
    isVisible: Boolean,
    onClick: () -> Unit
  ) {
    this.resetPasswordLabel.setOnClickListener {
      onClick()
    }
    this.resetPasswordLabel.isVisible = isVisible &&
      status is AccountLoginButtonStatus.AsLoginButtonEnabled
  }

  override fun clearActual() {
    // Nothing
  }

  fun configureFor(description: AccountProviderAuthenticationDescription.SAML2_0) {
    this.loginText =
      this.viewGroup.context.resources.getString(
        R.string.accountLoginWith, description.description
      )
    this.loginButton.text = this.loginText
  }

  companion object {
    fun bind(viewGroup: ViewGroup): ViewsForSAML20 {
      return ViewsForSAML20(
        viewGroup = viewGroup,
        loginButton = viewGroup.findViewById(R.id.authSAMLLogin),
        resetPasswordLabel = viewGroup.findViewById(R.id.resetPasswordLabel)
      )
    }
  }
}
