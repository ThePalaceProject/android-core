package org.nypl.simplified.ui.accounts

import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.view.isVisible
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Anonymous
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Basic
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.BasicToken
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.COPPAAgeGate
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.OAuthWithIntermediary
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.SAML2_0
import org.nypl.simplified.accounts.api.AccountUsername

import org.nypl.simplified.ui.accounts.view_bindings.AccountAuthenticationViewBindings
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForAnonymous
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForBasic
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForBasicToken
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForCOPPAAgeGate
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForOAuthWithIntermediary
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForSAML20

/**
 * A class that handles the visibility for a set of overlapping views.
 */

class AccountAuthenticationViews(
  private val viewGroup: ViewGroup,
  onUsernamePasswordChangeListener: (AccountUsername, AccountPassword) -> Unit
) {

  private val basic: ViewsForBasic =
    ViewsForBasic.bind(
      viewGroup = this.viewGroup.findViewById(R.id.authBasic),
      onUsernamePasswordChangeListener = onUsernamePasswordChangeListener
    )

  private val basicToken: ViewsForBasicToken =
    ViewsForBasicToken.bind(
      viewGroup = this.viewGroup.findViewById(R.id.authBasicToken),
      onUsernamePasswordChangeListener = onUsernamePasswordChangeListener
    )

  private val anonymous: ViewsForAnonymous =
    ViewsForAnonymous.bind(
      this.viewGroup.findViewById(R.id.authAnon)
    )
  private val oAuthWithIntermediary: ViewsForOAuthWithIntermediary =
    ViewsForOAuthWithIntermediary.bind(
      this.viewGroup.findViewById(R.id.authOAuthIntermediary)
    )
  private val saml20: ViewsForSAML20 =
    ViewsForSAML20.bind(
      this.viewGroup.findViewById(R.id.authSAML)
    )
  private val coppa: ViewsForCOPPAAgeGate =
    ViewsForCOPPAAgeGate.bind(
      this.viewGroup.findViewById(R.id.authCOPPA)
    )

  private val viewGroups =
    listOf<AccountAuthenticationViewBindings>(
      this.basic,
      this.basicToken,
      this.anonymous,
      this.oAuthWithIntermediary,
      this.saml20,
      this.coppa
    )

  private val dividers =
    listOf<View>(
      this.viewGroup.findViewById(R.id.authTopDivider),
      this.viewGroup.findViewById(R.id.authSpace),
      this.viewGroup.findViewById(R.id.authBottomDivider)
    )

  /**
   * Lock all of the views in the collection.
   *
   * @see [AccountAuthenticationViewBindings.lock]
   */

  fun lock() {
    this.viewGroups.forEach(AccountAuthenticationViewBindings::lock)
  }

  /**
   * Unlock all of the views in the collection.
   *
   * @see [AccountAuthenticationViewBindings.unlock]
   */

  fun unlock() {
    this.viewGroups.forEach(AccountAuthenticationViewBindings::unlock)
  }

  /**
   * Clear all of the views in the collection. Must only be called once.
   *
   * @see [AccountAuthenticationViewBindings.clear]
   */

  fun clear() {
    this.viewGroups.forEach(AccountAuthenticationViewBindings::clear)
  }

  /**
   * Set the status of all of the login buttons in the collection.
   *
   * @see [AccountAuthenticationViewBindings.setLoginButtonStatus]
   */

  fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    this.viewGroups.forEach {
      it.setLoginButtonStatus(status)
    }
  }

  /**
   * Set the status of the password reset label.
   *
   * @see [AccountAuthenticationViewBindings.setResetPasswordLabelStatus]
   */

  fun setResetPasswordLabelStatus(
    status: AccountLoginButtonStatus,
    isVisible: Boolean,
    onClick: () -> Unit
  ) {
    this.viewGroups.forEach {
      it.setResetPasswordLabelStatus(status, isVisible, onClick)
    }
  }

  /**
   * Set the visibility of the views such that the displayed view is the one that's suitable
   * for the given authentication description.
   */

  fun showFor(description: AccountProviderAuthenticationDescription) {
    this.dividers.forEach { it.isVisible = description !is Anonymous }
    this.viewGroups.forEach { it.viewGroup.visibility = GONE }

    return when (description) {
      is COPPAAgeGate ->
        this.coppa.viewGroup.visibility = VISIBLE
      is Basic -> {
        this.basic.viewGroup.visibility = VISIBLE
        this.basic.configureFor(description)
      }
      is BasicToken -> {
        this.basicToken.viewGroup.visibility = VISIBLE
        this.basicToken.configureFor(description)
      }
      is OAuthWithIntermediary -> {
        this.oAuthWithIntermediary.viewGroup.visibility = VISIBLE
      }
      Anonymous -> {
        this.anonymous.viewGroup.visibility = VISIBLE
      }
      is SAML2_0 -> {
        this.saml20.viewGroup.visibility = VISIBLE
        this.saml20.configureFor(description)
      }
    }
  }

  fun setBasicUserAndPass(
    user: String,
    password: String
  ) {
    this.basic.setUserAndPass(
      user = user,
      password = password
    )
  }

  fun setBasicTokenUserAndPass(
    user: String,
    password: String
  ) {
    this.basicToken.setUserAndPass(
      user = user,
      password = password
    )
  }

  /**
   * @return `true` if the views have all of the information required to attempt a login for the
   * given authentication description.
   */

  fun isSatisfiedFor(description: AccountProviderAuthenticationDescription): Boolean {
    return when (description) {
      is Basic -> {
        this.basic.isSatisfied(description)
      }
      is BasicToken -> {
        this.basicToken.isSatisfied(description)
      }
      is COPPAAgeGate,
      is OAuthWithIntermediary,
      Anonymous,
      is SAML2_0 ->
        true
    }
  }

  /**
   * Set the state of any COPPA gate related fields.
   */

  fun setCOPPAState(
    isOver13: Boolean,
    onAgeCheckboxClicked: (View) -> Unit
  ) {
    this.coppa.setState(
      isOver13 = isOver13,
      onAgeCheckboxClicked = onAgeCheckboxClicked
    )
  }

  /**
   * @return The current Basic authentication password
   */

  fun getBasicPassword(): AccountPassword {
    return this.basic.getPassword()
  }

  /**
   * @return The current Basic authentication username
   */

  fun getBasicUser(): AccountUsername {
    return this.basic.getUser()
  }

  /**
   * @return The current Basic authentication password
   */

  fun getBasicTokenPassword(): AccountPassword {
    return this.basicToken.getPassword()
  }

  /**
   * @return The current Basic authentication username
   */

  fun getBasicTokenUser(): AccountUsername {
    return this.basicToken.getUser()
  }
}
