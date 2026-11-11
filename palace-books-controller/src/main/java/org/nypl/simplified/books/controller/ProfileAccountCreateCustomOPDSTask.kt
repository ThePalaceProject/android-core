package org.nypl.simplified.books.controller

import io.reactivex.subjects.Subject
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.AllowRedirects.ALLOW_REDIRECTS
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation.AccountEventCreationFailed
import org.nypl.simplified.accounts.api.AccountEventCreation.AccountEventCreationInProgress
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.core.OPDSFeedConstants
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.profiles.controller.api.ProfileAccountCreationStringResourcesType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

/**
 * A task that creates an account based on a custom OPDS feed.
 */

class ProfileAccountCreateCustomOPDSTask(
  private val accountEvents: Subject<AccountEvent>,
  private val accountProviderRegistry: AccountProviderRegistryType,
  private val httpClient: LSHTTPClientType,
  private val opdsURI: URI,
  private val feedLoader: FeedLoaderType,
  private val profiles: ProfilesDatabaseType,
  private val strings: ProfileAccountCreationStringResourcesType,
) : Callable<TaskResult<AccountType>> {
  private var title: String = ""
  private var description: String = ""
  private val logger = LoggerFactory.getLogger(ProfileAccountCreateCustomOPDSTask::class.java)
  private val taskRecorder = TaskRecorder.create()

  override fun call(): TaskResult<AccountType> {
    this.publishProgressEvent(this.taskRecorder.beginNewStep(this.strings.creatingAccount))
    this.logger.debug("creating a custom OPDS account")

    return try {
      val accountProviderDescription =
        this.createAccountProviderDescription()

      val resolutionResult =
        this.accountProviderRegistry.resolve(
          { _, message ->
            this.publishProgressEvent(this.taskRecorder.beginNewStep(message))
          },
          accountProviderDescription,
        )

      when (resolutionResult) {
        is TaskResult.Success -> {
          this.createAccount(accountProviderDescription)
        }

        is TaskResult.Failure -> {
          this.accountResolutionFailed(resolutionResult)
        }
      }
    } catch (e: Throwable) {
      this.logger.error("account creation failed: ", e)
      this.taskRecorder.currentStepFailedAppending(
        message = this.strings.unexpectedException,
        errorCode = "unexpectedException",
        exception = e,
        extraMessages = listOf(),
      )
      this.publishFailureEvent()
      this.taskRecorder.finishFailure()
    }
  }

  private fun accountResolutionFailed(
    resolutionResult: TaskResult.Failure<AccountProviderType>,
  ): TaskResult.Failure<AccountType> {
    this.logger.error("could not resolve an account provider description")
    this.taskRecorder.addAll(resolutionResult.steps)
    this.taskRecorder.addAttributes(resolutionResult.attributes)
    this.taskRecorder.currentStepFailed(
      message = this.strings.resolvingAccountProviderFailed,
      errorCode = "resolvingAccountProviderFailed",
      extraMessages = listOf(),
    )
    return this.taskRecorder.finishFailure()
  }

  private fun createAccount(accountProviderDescription: AccountProviderDescription): TaskResult<AccountType> {
    val createResult =
      ProfileAccountCreateTask(
        accountEvents = this.accountEvents,
        accountProviderID = accountProviderDescription.id,
        accountProviders = this.accountProviderRegistry,
        profiles = this.profiles,
        strings = this.strings,
      ).call()

    return when (createResult) {
      is TaskResult.Success<AccountType> -> {
        this.taskRecorder.addAll(createResult.steps)
        val account = createResult.result
        account.setPreferences(account.preferences.copy(catalogURIOverride = this.opdsURI))
        this.publishProgressEvent(this.taskRecorder.currentStepSucceeded(this.strings.creatingAccountSucceeded))
        this.taskRecorder.finishSuccess(createResult.result)
      }

      is TaskResult.Failure<AccountType> -> {
        this.taskRecorder.addAll(createResult.steps)
        this.taskRecorder.finishFailure()
      }
    }
  }

  /**
   * Create a custom account provider description.
   */

  private fun createAccountProviderDescription(): AccountProviderDescription {
    this.logger.debug("creating an account provider description")
    this.publishProgressEvent(this.taskRecorder.beginNewStep(this.strings.creatingAnAccountProviderDescription))

    val authDocumentURI: URI? = this.findAuthenticationDocumentURI()
    val id = URI.create("urn:custom:" + UUID.randomUUID().toString())
    this.logger.debug("account id will be {}", id)

    val links =
      mutableListOf<Link>()

    links.add(
      Link.LinkBasic(
        href = this.opdsURI,
        relation = "http://opds-spec.org/catalog",
      ),
    )

    if (authDocumentURI != null) {
      links.add(
        Link.LinkBasic(
          href = authDocumentURI,
          type = MIMEType("application", "vnd.opds.authentication.v1.0+json", mapOf()),
          relation = OPDSFeedConstants.AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT,
        ),
      )
    }

    val description =
      AccountProviderDescription(
        id = id,
        title = this.title,
        description = this.description,
        updated = OffsetDateTime.now(ZoneOffset.UTC).withNano(0),
        links = links,
        images = listOf(),
      )

    /*
     * Publish the description to the account provider registry. It is now possible
     * to create an account using the description; the account creation task will ask
     * the registry to resolve the account provider description.
     */

    this.accountProviderRegistry.updateDescription(description)
    return description
  }

  private fun findAuthenticationDocumentURI(): URI? {
    this.logger.debug("creating an account provider description")

    this.publishProgressEvent(this.taskRecorder.beginNewStep(this.strings.findingAuthDocumentURI))
    this.publishProgressEvent(this.taskRecorder.beginNewStep(this.strings.fetchingOPDSFeed))

    val tempAccountID = AccountID(UUID.randomUUID())
    val future = this.feedLoader.fetchURI(tempAccountID, this.opdsURI, null, "GET")

    try {
      val result = future.get()

      when (result) {
        is FeedLoaderResult.FeedLoaderSuccess -> {
          this.logger.debug("fetched opds feed")
          this.publishProgressEvent(this.taskRecorder.beginNewStep(this.strings.parsingOPDSFeed))
          this.title = result.feed.feedTitle
          return result.feed.feedAuthDocument
        }

        is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication -> {
          this.logger.debug("failed to fetch opds feed: authentication required")
          this.taskRecorder.currentStepFailed(
            message = this.strings.fetchingOPDSFeedFailed,
            errorCode = "fetchingOPDSFeedFailed",
            extraMessages = listOf(),
          )
          this.publishFailureEvent()
          throw IOException()
        }

        is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral -> {
          this.logger.debug("failed to fetch opds feed: {}", result.message)
          this.taskRecorder.currentStepFailed(
            message = this.strings.fetchingOPDSFeedFailed,
            errorCode = "fetchingOPDSFeedFailed",
            extraMessages = listOf(),
          )
          this.publishFailureEvent()
          throw IOException()
        }
      }
    } catch (e: ExecutionException) {
      this.logger.debug("failed to fetch opds feed: ", e)
      this.taskRecorder.currentStepFailed(
        message = this.strings.fetchingOPDSFeedFailed,
        errorCode = "httpRequestFailed",
        exception = e,
        extraMessages = listOf(),
      )
      this.publishFailureEvent()
      throw e
    }
  }

  private fun publishFailureEvent() =
    this.accountEvents.onNext(AccountEventCreationFailed(this.taskRecorder.finishFailure<AccountType>()))

  private fun publishProgressEvent(step: TaskStep) =
    this.accountEvents.onNext(AccountEventCreationInProgress(step.description))
}
