package org.nypl.simplified.ui.accounts

import androidx.annotation.UiThread
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.main.MainNavigation

object AccountDetailModel {

  lateinit var account: AccountType

  var barcode: String? = null

  var showPleaseLoginTitle: Boolean = false

  fun enableBookmarkSyncing(
    enabled: Boolean
  ) {
    TODO()
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
}
