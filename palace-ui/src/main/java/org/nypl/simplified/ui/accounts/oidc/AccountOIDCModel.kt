package org.nypl.simplified.ui.accounts.oidc

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedInStaleCredentials
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingOut
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLogoutFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.ui.accounts.AccountEvents
import org.slf4j.LoggerFactory

object AccountOIDCModel {

  private val logger =
    LoggerFactory.getLogger(AccountOIDCModel::class.java)

  private var subscriptions: CloseableCollectionType<*>? = null

  fun start(
    activity: Activity,
    accountEvents: AccountEvents,
    account: AccountType
  ) {
    this.logger.debug("OIDC: Starting: {}", account.id)
    this.subscriptions?.close()
    this.subscriptions = CloseableCollection.create()

    val eventSub = accountEvents.events.subscribe { event ->
      try {
        this.onAccountEvent(
          activity = activity,
          account = account,
          event = event
        )
      } catch (e: Throwable) {
        this.logger.error("Error handling event: ", e)
      }
    }
    this.subscriptions?.add(AutoCloseable { eventSub.dispose() })
  }

  private fun onAccountEvent(
    activity: Activity,
    account: AccountType,
    event: AccountEvent
  ) {
    if (event !is AccountEventLoginStateChanged) {
      return
    }
    if (account.id != event.accountID) {
      return
    }

    this.logger.debug("OIDC: Login state changed: {} ({})", account.id, event.state.javaClass)
    when (val loginState = account.loginState) {
      is AccountLoggingInWaitingForExternalAuthentication -> {
        val intent = Intent(Intent.ACTION_VIEW, loginState.externalURI.toString().toUri())
        activity.startActivity(intent)
      }

      is AccountLoggingIn -> {
        // Nothing required.
      }

      is AccountLoggedIn,
      is AccountLoggedInStaleCredentials,
      is AccountLoggingOut,
      is AccountLoginFailed,
      is AccountLogoutFailed,
      is AccountNotLoggedIn -> {
        this.logger.debug("OIDC: Unsubscribing from account events: {}", account.id)
        this.subscriptions?.close()
      }
    }
  }
}
