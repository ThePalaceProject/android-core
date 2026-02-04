package org.nypl.simplified.ui.accounts.view_bindings

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus
import org.nypl.simplified.ui.accounts.AccountLogoutButtonStatus

class ViewsForSAML20(
  override val viewGroup: ViewGroup,
  private val loginButton: Button,
  private val logoutButton: Button,
  private val resetPasswordLabel: TextView
) : AccountAuthenticationViewBindings() {

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

  private fun setVisibility(
    view: View,
    v: Int
  ) {
    if (view.visibility != v) {
      view.visibility = v
    }
  }

  override fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    return when (status) {
      is AccountLoginButtonStatus.AsLoginButtonEnabled -> {
        this.loginButton.isEnabled = true
        this.loginButton.text = this.loginText
        this.loginButton.setOnClickListener { status.onClick.invoke() }
        this.setVisibility(this.loginButton, View.VISIBLE)
      }

      AccountLoginButtonStatus.AsLoginButtonDisabled -> {
        this.loginButton.isEnabled = false
        this.loginButton.text = this.loginText
        this.setVisibility(this.loginButton, View.VISIBLE)
      }

      is AccountLoginButtonStatus.AsCancelButtonEnabled -> {
        this.loginButton.isEnabled = true
        this.loginButton.text = this.cancelText
        this.loginButton.setOnClickListener { status.onClick.invoke() }
        this.setVisibility(this.loginButton, View.VISIBLE)
      }

      AccountLoginButtonStatus.AsCancelButtonDisabled -> {
        this.loginButton.isEnabled = false
        this.loginButton.text = this.cancelText
        this.setVisibility(this.loginButton, View.VISIBLE)
      }

      AccountLoginButtonStatus.AsButtonGone -> {
        this.setVisibility(this.loginButton, View.GONE)
      }
    }
  }

  override fun setLogoutButtonStatus(status: AccountLogoutButtonStatus) {
    return when (status) {
      is AccountLogoutButtonStatus.AsLogoutButtonEnabled -> {
        this.logoutButton.setText(R.string.accountLogout)
        this.logoutButton.isEnabled = true
        this.logoutButton.setOnClickListener { status.onClick.invoke() }
        this.setVisibility(this.logoutButton, View.VISIBLE)
      }

      AccountLogoutButtonStatus.AsLogoutButtonDisabled -> {
        this.logoutButton.setText(R.string.accountLogout)
        this.logoutButton.isEnabled = false
        this.setVisibility(this.logoutButton, View.VISIBLE)
      }

      is AccountLogoutButtonStatus.AsCancelButtonEnabled -> {
        this.logoutButton.setText(R.string.accountCancel)
        this.logoutButton.isEnabled = true
        this.logoutButton.setOnClickListener { status.onClick.invoke() }
        this.setVisibility(this.logoutButton, View.VISIBLE)
      }

      AccountLogoutButtonStatus.AsCancelButtonDisabled -> {
        this.logoutButton.setText(R.string.accountCancel)
        this.logoutButton.isEnabled = false
        this.setVisibility(this.logoutButton, View.VISIBLE)
      }

      AccountLogoutButtonStatus.AsButtonGone -> {
        this.setVisibility(this.logoutButton, View.GONE)
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

  override fun blank() {
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
        logoutButton = viewGroup.findViewById(R.id.authSAMLLogout),
        resetPasswordLabel = viewGroup.findViewById(R.id.resetPasswordLabel)
      )
    }
  }
}
