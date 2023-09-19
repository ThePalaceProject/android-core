package org.nypl.simplified.ui.accounts.view_bindings

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus
import org.nypl.simplified.ui.accounts.OnTextChangeListener
import org.slf4j.LoggerFactory

class ViewsForBasic(
  override val viewGroup: ViewGroup,
  val pass: TextInputEditText,
  val passLabel: TextInputLayout,
  val showPass: CheckBox,
  val user: TextInputEditText,
  val userLabel: TextInputLayout,
  val onUsernamePasswordChangeListener: (AccountUsername, AccountPassword) -> Unit,
  val loginButton: Button,
  val resetPasswordLabel: TextView
) : AccountAuthenticationViewBindings() {

  private val logger = LoggerFactory.getLogger(ViewsForBasic::class.java)

  private var showPassClicked = false

  private val userTextListener =
    OnTextChangeListener(
      onChanged = { _, _, _, _ ->
        this.onUsernamePasswordChangeListener.invoke(
          AccountUsername(this.user.text.toString()),
          AccountPassword(this.pass.text.toString())
        )
      }
    )

  private val passTextListener =
    OnTextChangeListener(
      onChanged = { _, _, _, _ ->

        if (showPassClicked) {
          showPassClicked = false
          return@OnTextChangeListener
        }

        this.onUsernamePasswordChangeListener.invoke(
          AccountUsername(this.user.text.toString()),
          AccountPassword(this.pass.text.toString())
        )
      }
    )

  init {

    /*
     * Configure a checkbox listener that shows and hides the password field. Note that
     * this will trigger the "text changed" listener on the password field, so we are using
     * a flag to determine when this listener is called from enabling/disabling the checkbox
     * or not.
     */

    this.showPass.setOnCheckedChangeListener { _, isChecked ->
      showPassClicked = true
      setPasswordVisible(isChecked)
    }

    this.user.addTextChangedListener(this.userTextListener)
    this.pass.addTextChangedListener(this.passTextListener)
  }

  private fun setPasswordVisible(visible: Boolean) {
    this.pass.transformationMethod =
      if (visible) {
        null
      } else {
        PasswordTransformationMethod.getInstance()
      }

    // Reset the cursor position
    this.pass.setSelection(this.pass.length())
  }

  override fun lock() {
    this.user.isEnabled = false
    this.pass.isEnabled = false
  }

  override fun unlock() {
    this.user.isEnabled = true
    this.pass.isEnabled = true
  }

  override fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    return when (status) {
      is AccountLoginButtonStatus.AsLoginButtonEnabled -> {
        this.loginButton.setText(R.string.accountLogin)
        this.loginButton.isEnabled = true
        this.loginButton.setOnClickListener { status.onClick.invoke() }
      }
      AccountLoginButtonStatus.AsLoginButtonDisabled -> {
        this.loginButton.setText(R.string.accountLogin)
        this.loginButton.isEnabled = false
      }
      is AccountLoginButtonStatus.AsCancelButtonEnabled -> {
        this.loginButton.setText(R.string.accountCancel)
        this.loginButton.isEnabled = true
        this.loginButton.setOnClickListener { status.onClick.invoke() }
      }
      is AccountLoginButtonStatus.AsLogoutButtonEnabled -> {
        this.loginButton.setText(R.string.accountLogout)
        this.loginButton.isEnabled = true
        this.loginButton.setOnClickListener { status.onClick.invoke() }
      }
      AccountLoginButtonStatus.AsLogoutButtonDisabled -> {
        this.loginButton.setText(R.string.accountLogout)
        this.loginButton.isEnabled = false
      }
      AccountLoginButtonStatus.AsCancelButtonDisabled -> {
        this.loginButton.setText(R.string.accountCancel)
        this.loginButton.isEnabled = false
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
    this.resetPasswordLabel.isVisible = isVisible && (
      status is AccountLoginButtonStatus.AsLoginButtonEnabled ||
        status is AccountLoginButtonStatus.AsLoginButtonDisabled
      )
  }

  override fun blank() {
    this.setUserAndPass("", "")
  }

  fun setUserAndPass(
    user: String,
    password: String
  ) {
    this.user.setText(user, TextView.BufferType.EDITABLE)
    this.pass.setText(password, TextView.BufferType.EDITABLE)
  }

  fun isSatisfied(description: AccountProviderAuthenticationDescription.Basic): Boolean {
    val noUserRequired =
      description.keyboard == AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT
    val noPasswordRequired =
      description.passwordKeyboard == AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT
    val userOk =
      !this.user.text.isNullOrBlank() || noUserRequired
    val passOk =
      !this.pass.text.isNullOrBlank() || noPasswordRequired
    return userOk && passOk
  }

  fun configureFor(description: AccountProviderAuthenticationDescription.Basic) {
    val res = this.viewGroup.resources

    // Set input labels
    this.userLabel.hint =
      description.labels["LOGIN"] ?: res.getString(R.string.accountUserName)
    this.passLabel.hint =
      description.labels["PASSWORD"] ?: res.getString(R.string.accountPassword)
    this.showPass.text =
      res.getString(
        R.string.accountPasswordShow,
        (description.labels["PASSWORD"] ?: res.getString(R.string.accountPassword))
      )

    // Set input types
    this.logger.debug("Setting {} for user input type", description.keyboard)
    this.user.inputType = when (description.keyboard) {
      AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
      AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT -> {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
      }
      AccountProviderAuthenticationDescription.KeyboardInput.EMAIL_ADDRESS -> {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
      }
      AccountProviderAuthenticationDescription.KeyboardInput.NUMBER_PAD -> {
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
      }
    }

    this.logger.debug("Setting {} for password input type", description.passwordKeyboard)
    this.pass.inputType = when (description.passwordKeyboard) {
      AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
      AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT,
      AccountProviderAuthenticationDescription.KeyboardInput.EMAIL_ADDRESS -> {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      }
      AccountProviderAuthenticationDescription.KeyboardInput.NUMBER_PAD -> {
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      }
    }

    // Toggle visibility of fields
    this.userLabel.isVisible =
      description.keyboard != AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT
    this.passLabel.isVisible =
      description.passwordKeyboard != AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT
    this.showPass.isVisible =
      description.passwordKeyboard != AccountProviderAuthenticationDescription.KeyboardInput.NO_INPUT

    // Reset password visibility
    setPasswordVisible(this.showPass.isChecked)
  }

  fun getPassword(): AccountPassword {
    return AccountPassword(this.pass.text.toString().trim())
  }

  fun getUser(): AccountUsername {
    return AccountUsername(this.user.text.toString().trim())
  }

  companion object {
    fun bind(
      viewGroup: ViewGroup,
      onUsernamePasswordChangeListener: (AccountUsername, AccountPassword) -> Unit
    ): ViewsForBasic {
      return ViewsForBasic(
        viewGroup = viewGroup,
        pass = viewGroup.findViewById(R.id.authBasicPassField),
        passLabel = viewGroup.findViewById(R.id.authBasicPassLabel),
        user = viewGroup.findViewById(R.id.authBasicUserField),
        userLabel = viewGroup.findViewById(R.id.authBasicUserLabel),
        showPass = viewGroup.findViewById(R.id.authBasicShowPass),
        onUsernamePasswordChangeListener = onUsernamePasswordChangeListener,
        loginButton = viewGroup.findViewById(R.id.authBasicLogin),
        resetPasswordLabel = viewGroup.findViewById(R.id.resetPasswordLabel)
      )
    }
  }
}
