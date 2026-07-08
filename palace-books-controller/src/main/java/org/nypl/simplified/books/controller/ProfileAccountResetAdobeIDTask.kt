package org.nypl.simplified.books.controller

import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Delete
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedInStaleCredentials
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingOut
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLogoutFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.AccountProviderResolution
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

/**
 * A task that resets an Adobe ID for the given account.
 */

class ProfileAccountResetAdobeIDTask(
  private val accountProviderResolutionStrings: AccountProviderResolutionStringsType,
  private val accountID: AccountID,
  private val http: LSHTTPClientType,
  private val profile: ProfileReadableType,
  private val authDocumentParsers: AuthenticationDocumentParsersType
) : Callable<TaskResult<*>> {
  private lateinit var account: AccountType
  private lateinit var credentials: AccountAuthenticationCredentials

  private val logger =
    LoggerFactory.getLogger(ProfileAccountResetAdobeIDTask::class.java)

  private val steps =
    TaskRecorder.create()

  private fun warn(
    message: String,
    vararg arguments: Any?
  ) = this.logger.warn("[{}][{}] $message", this.profile.id.uuid, this.accountID, *arguments)

  override fun call(): TaskResult<Unit> {
    this.steps.beginNewStep("Resetting Adobe ID...")

    return try {
      this.account = this.profile.account(this.accountID)

      this.credentials =
        when (val state = this.account.loginState) {
          is AccountLoggedInStaleCredentials -> {
            state.credentials
          }

          is AccountLoggedIn -> {
            state.credentials
          }

          is AccountLogoutFailed -> {
            state.credentials
          }

          is AccountLoggingOut -> {
            state.credentials
          }

          is AccountNotLoggedIn,
          is AccountLoggingIn,
          is AccountLoginFailed,
          is AccountLoggingInWaitingForExternalAuthentication -> {
            this.warn("Account is not logged in.")
            this.steps.currentStepFailed(
              message = "Account is not logged in.",
              errorCode = "error-reset-failed-not-logged-in",
              exception = null,
              extraMessages = listOf()
            )
            return this.steps.finishFailure()
          }
        }

      this.steps.addAttribute("AccountProviderName", this.account.provider.displayName)
      this.steps.addAttribute("AccountProviderID",
        this.account.provider.id
          .toString()
      )

      this.steps.beginNewStep("Fetching authentication document.")
      val resolution =
        AccountProviderResolution(
          stringResources = this.accountProviderResolutionStrings,
          authDocumentParsers = this.authDocumentParsers,
          http = this.http,
          description = this.account.provider.toDescription()
        )

      val authDocument =
        resolution.fetchAuthDocument(
          this.steps,
          onProgress = { _, _ ->
            // Ignored
          }
        )
      if (authDocument == null) {
        this.steps.currentStepFailed(
          message = "Failed to fetch auth document.",
          errorCode = "error-reset-failed-fetch-auth-document",
          exception = null,
          extraMessages = listOf()
        )
        return this.steps.finishFailure()
      }
      this.steps.currentStepSucceeded("Fetched authentication document.")

      this.steps.beginNewStep("Calling Adobe ID reset endpoint.")
      val adobeResetURI = authDocument.adobeResetURI
      if (adobeResetURI == null) {
        this.steps.currentStepFailed(
          message = "Failed to fetch Adobe reset URI.",
          errorCode = "error-reset-no-reset-uri",
          exception = null,
          extraMessages = listOf()
        )
        return this.steps.finishFailure()
      }

      val octetStream =
        MIMEType("application", "octet-stream", mapOf())

      val request =
        this.http
          .newRequest(adobeResetURI)
          .setAuthorization(AccountAuthenticatedHTTP.createAuthorizationIfPresent(account.loginState.credentials))
          .setMethod(Delete(ByteArray(0), octetStream))
          .build()

      val response = request.execute()
      when (val status = response.status) {
        is LSHTTPResponseStatus.Failed -> {
          this.steps.currentStepFailed(
            message = "Connecting to the server failed..",
            errorCode = "connectionFailed",
            exception = status.exception,
            extraMessages = listOf()
          )
          this.steps.finishFailure()
        }

        is LSHTTPResponseStatus.Responded.Error -> {
          this.steps.addAttributesIfPresent(status.properties.problemReport?.toMap())
          this.steps.currentStepFailed(
            message = "Server error: ${status.properties.status} ${status.properties.message}",
            errorCode = "httpError ${status.properties.status} $adobeResetURI",
            exception = null,
            extraMessages = listOf()
          )
          this.steps.finishFailure()
        }

        is LSHTTPResponseStatus.Responded.OK -> {
          this.steps.currentStepSucceeded("Server accepted the reset request.")
          this.steps.finishSuccess(Unit)
        }
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed: ", e)
      this.steps.currentStepFailedAppending(
        message = "Unexpected exception.",
        errorCode = "unexpectedException",
        exception = e,
        extraMessages = listOf()
      )
      this.steps.finishFailure()
    }
  }
}
