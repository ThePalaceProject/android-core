package org.nypl.simplified.ui.accounts

import androidx.annotation.UiThread
import com.google.common.util.concurrent.FluentFuture
import io.reactivex.disposables.Disposable
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainNavigation
import java.util.UUID
import java.util.concurrent.Flow

object AccountDetailModel {

  lateinit var account: AccountType

  var barcode: String? = null

  var showPleaseLoginTitle: Boolean = false

  fun enableBookmarkSyncing(
    enabled: Boolean
  ) {
    this.account.setPreferences(
      this.account.preferences.copy(bookmarkSyncingPermitted = enabled)
    )
  }

  fun tryLogout() {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    profiles.profileAccountLogout(this.account.id)
  }

  fun tryLogin(
    request: ProfileAccountLoginRequest
  ) {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val loginFuture =
      profiles.profileAccountLogin(request)

  }

  @UiThread
  fun openErrorPage(
    taskSteps: List<TaskStep>
  ) {
    UIThread.checkIsUIThread()

    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    MainNavigation.openErrorPage(
      ErrorPageParameters(
        emailAddress = buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[palace-error-report]",
        attributes = sortedMapOf(),
        taskSteps = taskSteps
      )
    )
  }

  private val executeAfterLoginSubscriptions =
    mutableMapOf<UUID, Disposable>()

  fun executeAfterLogin(
    accountID: AccountID,
    runOnSuccess: () -> Unit,
    runOnFailure: () -> Unit
  ): AutoCloseable {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    val id =
      UUID.randomUUID()

    fun cleanUp() {
      this.executeAfterLoginSubscriptions.remove(id)?.dispose()
    }

    val subscription =
      profiles.accountEvents()
        .ofType(AccountEventLoginStateChanged::class.java)
        .filter { event -> event.accountID == accountID }
        .subscribe { event ->
          when (event.state) {
            is AccountLoggingIn,
            is AccountLoginState.AccountLoggingInWaitingForExternalAuthentication,
            is AccountLoginState.AccountLoggingOut -> {
              // Still in progress!
            }

            AccountLoginState.AccountNotLoggedIn,
            is AccountLoginState.AccountLogoutFailed,
            is AccountLoginState.AccountLoginFailed -> {
              cleanUp()
              runOnFailure()
            }

            is AccountLoginState.AccountLoggedIn -> {
              cleanUp()
              runOnSuccess()
            }
          }
        }

    this.executeAfterLoginSubscriptions[id] = subscription
    return AutoCloseable { cleanUp() }
  }
}
