package org.nypl.simplified.ui.accounts

import androidx.annotation.UiThread
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
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainNavigation
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

object AccountDetailModel {

  private val logger =
    LoggerFactory.getLogger(AccountDetailModel::class.java)

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

  private data class AfterLoginTask(
    val id: UUID,
    val disposable: Disposable
  )

  private val executeAfterLoginSubscription: AtomicReference<AfterLoginTask> =
    AtomicReference()

  fun executeAfterLogin(
    accountID: AccountID,
    runOnLogin: () -> Unit
  ) {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    this.clearPendingAfterLoginTask()

    this.executeAfterLoginSubscription.getAndSet(
      AfterLoginTask(UUID.randomUUID(),
        profiles.accountEvents()
          .ofType(AccountEventLoginStateChanged::class.java)
          .filter { event -> event.accountID == accountID }
          .subscribe { event ->
            when (event.state) {
              is AccountLoggingIn,
              is AccountLoginState.AccountLoggingInWaitingForExternalAuthentication,
              is AccountLoginState.AccountLoggingOut,
              AccountLoginState.AccountNotLoggedIn,
              is AccountLoginState.AccountLogoutFailed,
              is AccountLoginState.AccountLoginFailed -> {
                // Nothing to do
              }

              is AccountLoginState.AccountLoggedIn -> {
                this.clearPendingAfterLoginTask()
                runOnLogin()
              }
            }
          }
      )
    )
  }

  fun clearPendingAfterLoginTask() {
    try {
      val existing =
        this.executeAfterLoginSubscription.getAndSet(null)

      if (existing != null) {
        existing.disposable.dispose()
        this.logger.debug("Cleared pending AfterLogin task {}", existing.id)
      }
    } catch (e: Throwable) {
      // Nothing useful to do with this.
    }
  }
}
