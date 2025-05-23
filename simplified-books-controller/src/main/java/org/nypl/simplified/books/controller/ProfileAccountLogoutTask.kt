package org.nypl.simplified.books.controller

import com.google.common.base.Preconditions
import one.irradia.mime.api.MIMECompatibility
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.mdc.MDCKeys
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingOut
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLogoutFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.accounts.api.AccountLogoutStringResourcesType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.notifications.NotificationTokenHTTPCallsType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.patron.api.PatronDRMAdobe
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.IOException
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A task that performs a logout for the given account in the given profile.
 */

class ProfileAccountLogoutTask(
  private val account: AccountType,
  private val adeptExecutor: AdobeAdeptExecutorType?,
  private val bookRegistry: BookRegistryType,
  private val feedLoader: FeedLoaderType,
  private val http: LSHTTPClientType,
  private val logoutStrings: AccountLogoutStringResourcesType,
  private val notificationTokenHttpCalls: NotificationTokenHTTPCallsType,
  private val patronParsers: PatronUserProfileParsersType,
  private val profile: ProfileReadableType
) : Callable<TaskResult<Unit>> {

  private class StepFailedHandled(override val cause: Throwable) : Exception()

  init {
    Preconditions.checkState(
      this.profile.accounts().containsKey(this.account.id),
      "Profile must contain the given account"
    )
  }

  private lateinit var credentials: AccountAuthenticationCredentials

  private val logger =
    LoggerFactory.getLogger(ProfileAccountLogoutTask::class.java)

  private val steps =
    TaskRecorder.create()

  private fun warn(message: String, vararg arguments: Any?) =
    this.logger.warn("[{}][{}] $message", this.profile.id.uuid, this.account.id, *arguments)

  private fun debug(message: String, vararg arguments: Any?) =
    this.logger.debug("[{}][{}] $message", this.profile.id.uuid, this.account.id, *arguments)

  private fun error(message: String, vararg arguments: Any?) =
    this.logger.error("[{}][{}] $message", this.profile.id.uuid, this.account.id, *arguments)

  override fun call(): TaskResult<Unit> {
    this.steps.beginNewStep(this.logoutStrings.logoutStarted)

    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, this.account.id.uuid.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_NAME, this.account.provider.displayName)
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, this.account.provider.id.toString())

    this.credentials =
      when (val state = this.account.loginState) {
        is AccountLoggedIn -> state.credentials
        is AccountLogoutFailed -> state.credentials
        is AccountLoggingOut -> state.credentials
        is AccountNotLoggedIn,
        is AccountLoggingIn,
        is AccountLoginFailed,
        is AccountLoggingInWaitingForExternalAuthentication -> {
          this.warn("attempted to sign out with account in state {}", state.javaClass.canonicalName)
          this.steps.currentStepSucceeded(this.logoutStrings.logoutNotLoggedIn)
          return this.steps.finishSuccess(Unit)
        }
      }

    return try {
      this.runFCMTokenDeletion()
      this.runDeviceDeactivation()
      this.runUpdateOPDSEntries()
      this.runBookRegistryClear()
      this.account.setLoginState(AccountNotLoggedIn)
      return this.steps.finishSuccess(Unit)
    } catch (e: Throwable) {
      this.steps.currentStepFailedAppending(
        message = this.logoutStrings.logoutUnexpectedException,
        errorCode = "unexpectedException",
        exception = e,
        extraMessages = listOf()
      )

      val failure = this.steps.finishFailure<Unit>()
      this.account.setLoginState(AccountLogoutFailed(failure, this.credentials))
      failure
    }
  }

  private fun runDeviceDeactivation() {
    this.debug("running device deactivation")

    this.steps.beginNewStep(this.logoutStrings.logoutDeactivatingDeviceAdobe)
    this.updateLoggingOutState()

    val adobeCredentialsMaybe = this.credentials.adobeCredentials
    if (adobeCredentialsMaybe != null) {
      this.runDeviceDeactivationAdobe(adobeCredentialsMaybe)
      return
    }
  }

  private fun runFCMTokenDeletion() {
    this.debug("running fcm token deletion")

    notificationTokenHttpCalls.deleteFCMTokenForProfileAccount(
      account = account
    )
  }

  private fun handlePatronUserProfile(): PatronDRMAdobe? {
    val patronProfile =
      PatronUserProfiles.runPatronProfileRequest(
        taskRecorder = this.steps,
        patronParsers = this.patronParsers,
        credentials = this.credentials,
        http = this.http,
        account = this.account
      )
    return patronProfile.drm
      .filterIsInstance<PatronDRMAdobe>()
      .firstOrNull()
  }

  private fun runDeviceDeactivationAdobe(
    adobeCredentials: AccountAuthenticationAdobePreActivationCredentials
  ) {
    val postActivation = adobeCredentials.postActivationCredentials
    if (postActivation == null) {
      this.debug("device does not appear to be activated")
      this.steps.currentStepSucceeded(this.logoutStrings.logoutDeactivatingDeviceAdobeNotActive)
      return
    }

    /*
     * If the Adept executor is not provided, it means that this build of the application
     * has no support for Adobe DRM. We don't treat a missing Adept executor as failure case
     * because if support for Adobe DRM is dropped in the future, it would suddenly become
     * impossible for users to "log out" with activated devices.
     */

    val adeptExecutor = this.adeptExecutor
    if (adeptExecutor == null) {
      this.warn("device is activated but DRM is unsupported")
      this.steps.currentStepSucceeded(this.logoutStrings.logoutDeactivatingDeviceAdobeUnsupported)
      return
    }

    this.debug("device is activated and DRM is supported, running deactivation")
    val token = this.handlePatronUserProfile()
    if (token == null) {
      this.warn("Patron user profile contained no Adobe DRM client token")
      val message = "Patron user profile is missing DRM information."
      this.steps.currentStepFailed(
        message = message,
        errorCode = "patronUserProfileNoDRM",
        extraMessages = listOf()
      )
      throw IOException(message)
    }

    val adeptFuture =
      AdobeDRMExtensions.deactivateDevice(
        executor = adeptExecutor,
        error = { message -> this.error(message) },
        debug = { message -> this.debug(message) },
        vendorID = adobeCredentials.vendorID,
        userID = postActivation.userID,
        clientToken = AccountAuthenticationAdobeClientToken.parse(token.clientToken)
      )

    try {
      adeptFuture.get(1L, TimeUnit.MINUTES)
    } catch (e: ExecutionException) {
      val ex = e.cause!!
      this.logger.debug("exception raised waiting for adept future: ", ex)
      this.handleAdobeDRMConnectorException(ex)
      throw ex
    } catch (e: Throwable) {
      this.logger.debug("exception raised waiting for adept future: ", e)
      this.handleAdobeDRMConnectorException(e)
      throw e
    }

    this.credentials = this.credentials.withoutAdobePostActivationCredentials()
    this.steps.currentStepSucceeded(this.logoutStrings.logoutDeactivatingDeviceAdobeDeactivated)

    adobeCredentials.deviceManagerURI?.let { uri ->
      this.runDeviceDeactivationAdobeSendDeviceManagerRequest(uri)
    }
  }

  private fun runDeviceDeactivationAdobeSendDeviceManagerRequest(
    deviceManagerURI: URI
  ) {
    this.debug("runDeviceDeactivationAdobeSendDeviceManagerRequest: posting device ID")

    this.steps.beginNewStep(this.logoutStrings.logoutDeviceDeactivationPostDeviceManager)
    this.updateLoggingOutState()

    /*
     * We don't care if this fails.
     *
     * XXX: We're not passing the device ID here!
     */

    val request =
      this.http.newRequest(deviceManagerURI)
        .setAuthorization(AccountAuthenticatedHTTP.createAuthorizationIfPresent(this.credentials))
        .setMethod(
          LSHTTPRequestBuilderType.Method.Delete(
            ByteArray(0),
            MIMECompatibility.applicationOctetStream
          )
        )
        .addHeader("Content-Type", "vnd.librarysimplified/drm-device-id-list")
        .build()

    request.execute()

    this.steps.currentStepSucceeded(this.logoutStrings.logoutDeviceDeactivationPostDeviceManagerFinished)
  }

  private fun handleAdobeDRMConnectorException(ex: Throwable) =
    when (ex) {
      is AdobeDRMExtensions.AdobeDRMLogoutConnectorException -> {
        val message = this.logoutStrings.logoutDeactivatingDeviceAdobeFailed(ex.errorCode, ex)
        this.steps.currentStepFailed(
          message,
          errorCode = "Adobe ACS: ${ex.errorCode}",
          exception = ex,
          extraMessages = listOf()
        )
      }

      else -> {
        this.steps.currentStepFailed(
          message = this.logoutStrings.logoutDeactivatingDeviceAdobeFailed("UNKNOWN", ex),
          errorCode = "unexpectedException",
          exception = ex,
          extraMessages = listOf()
        )
      }
    }

  private fun updateLoggingOutState(status: String? = null) {
    this.account.setLoginState(
      AccountLoggingOut(
        this.credentials,
        status ?: this.steps.currentStep()?.description ?: ""
      )
    )
  }

  private fun runUpdateOPDSEntries() {
    this.debug("updating OPDS entries in the database")
    this.updateLoggingOutState("Updating OPDS entries in the database.")

    for (book in this.account.bookDatabase.books()) {
      val stepDesc = this.logoutStrings.logoutUpdatingOPDSEntry(book.toString())
      this.debug(stepDesc)
      this.steps.beginNewStep(stepDesc)

      try {
        val entry = account.bookDatabase.entry(book)
        val alternate = entry.book.entry.alternate.getOrNull()
        if (alternate == null) {
          this.error("no alternate link available for book $book. skipping...")
          val message = this.logoutStrings.logoutNoAlternateLinkInDatabase
          this.steps.currentStepFailed(
            message = message,
            errorCode = "noAlternateLink",
            extraMessages = listOf()
          )
        } else {
          val newFeedEntry = this.fetchOPDSEntry(alternate)
          entry.writeOPDSEntry(newFeedEntry)
        }
      } catch (e: StepFailedHandled) {
        this.error("step failed with exception", e.cause)
      } catch (e: Exception) {
        this.error("step failed with unexpected exception", e)
        val message = this.logoutStrings.logoutUnexpectedException
        this.steps.currentStepFailed(
          message,
          errorCode = "unexpectedException",
          exception = e,
          extraMessages = listOf()
        )
      }
    }
  }

  private fun fetchOPDSEntry(uri: URI): OPDSAcquisitionFeedEntry {
    val feedResult = try {
      this.feedLoader.fetchURI(
        accountID = this.account.id,
        uri = uri,
        credentials = null,
        method = "GET"
      ).get()
    } catch (e: TimeoutException) {
      val message = this.logoutStrings.logoutOPDSFeedTimedOut
      this.steps.currentStepFailed(
        message = message,
        "timedOut",
        exception = e,
        extraMessages = listOf()
      )
      throw StepFailedHandled(e)
    } catch (e: ExecutionException) {
      throw e.cause!!
    }

    val feed =
      when (feedResult) {
        is FeedLoaderResult.FeedLoaderSuccess ->
          feedResult.feed

        is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication -> {
          this.debug(feedResult.message)
          // FIXME: Some servers require authentication
          throw feedResult.exception
        }

        is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral -> {
          val message = this.logoutStrings.logoutOPDSFeedFailed
          this.steps.currentStepFailed(
            message = message,
            errorCode = "feedLoaderFailed",
            exception = feedResult.exception,
            extraMessages = listOf()
          )
          throw StepFailedHandled(feedResult.exception)
        }
      }

    if (feed.size == 0) {
      val message = this.logoutStrings.logoutOPDSFeedEmpty
      val exception = Exception(message)
      this.steps.currentStepFailed(
        message = message,
        errorCode = "feedEmpty",
        exception = exception,
        extraMessages = listOf()
      )
      throw StepFailedHandled(exception)
    }

    return when (feed) {
      is Feed.FeedWithoutGroups -> {
        when (val feedEntry = feed.entriesInOrder[0]) {
          is FeedEntry.FeedEntryCorrupt -> {
            val message = this.logoutStrings.logoutOPDSFeedCorrupt
            this.steps.currentStepFailed(
              message = message,
              errorCode = "feedCorrupted",
              exception = feedEntry.error,
              extraMessages = listOf()
            )
            throw StepFailedHandled(feedEntry.error)
          }

          is FeedEntry.FeedEntryOPDS ->
            feedEntry.feedEntry
        }
      }

      is Feed.FeedWithGroups -> {
        val message = this.logoutStrings.logoutOPDSFeedWithGroups
        val exception = Exception(message)
        this.steps.currentStepFailed(
          message = message,
          errorCode = "feedUnusable",
          exception = exception,
          extraMessages = listOf()
        )
        throw StepFailedHandled(exception)
      }
    }
  }

  private fun runBookRegistryClear() {
    this.debug("clearing book database and updating registry")

    this.steps.beginNewStep(this.logoutStrings.logoutClearingBookDatabase)
    this.updateLoggingOutState()
    try {
      for (book in this.account.bookDatabase.books()) {
        val entry = account.bookDatabase.entry(book)
        val newBook = entry.book.copy(formats = emptyList())
        entry.delete()
        val status = BookStatus.fromBook(newBook)
        this.bookRegistry.update(BookWithStatus(entry.book, status))
      }
    } catch (e: Throwable) {
      this.error("could not clear book database: ", e)
      this.steps.currentStepFailed(
        message = this.logoutStrings.logoutClearingBookDatabaseFailed,
        errorCode = "unexpectedException",
        extraMessages = listOf()
      )
    }
  }
}
