package org.thepalaceproject.opds.client.internal

import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSState
import org.thepalaceproject.opds.client.OPDSState.OPDSStateHistoryParticipant

internal class OPDSCmdExecuteRequest(
  private val request: OPDSClientRequest
) : OPDSCmd() {

  private val logger =
    LoggerFactory.getLogger(OPDSCmdExecuteRequest::class.java)

  override fun execute(context: OPDSCmdContextType) {
    if (this.taskFuture.isCancelled) {
      context.operationCancelled()
      return
    }

    try {
      return when (val r = this.request) {
        is OPDSClientRequest.ExistingEntry -> this.commandFetchExistingEntry(context, r)
        is OPDSClientRequest.NewFeed -> this.commandFetchNewFeed(context, r)
      }
    } catch (e: Throwable) {
      this.taskFuture.completeExceptionally(e)
    }
  }

  private fun commandFetchNewFeed(
    context: OPDSCmdContextType,
    request: OPDSClientRequest.NewFeed
  ) {
    context.setState(OPDSState.Loading(request))

    val future0 =
      context.feedLoader.fetchURI(
        accountID = request.accountID,
        uri = request.uri,
        credentials = request.credentials,
        method = request.method
      )

    future0.handle { result, throwable ->
      try {
        this.taskFuture.complete(this.processNewFeed(context, throwable, request, result))
      } catch (e: Throwable) {
        this.taskFuture.completeExceptionally(e)
      }
    }
  }

  private fun feedException(
    request: OPDSClientRequest.NewFeed,
    throwable: Throwable
  ): PresentableErrorType {
    return FeedLoaderFailedGeneral(
      problemReport = null,
      exception = Exception(throwable),
      message = throwable.message ?: "Unexpected error occurred.",
      attributesInitial = mapOf(
        Pair("URI", request.uri.toString())
      )
    )
  }

  private fun processNewFeed(
    context: OPDSCmdContextType,
    throwable: Throwable?,
    request: OPDSClientRequest.NewFeed,
    result: FeedLoaderResult?
  ) {
    if (this.taskFuture.isCancelled) {
      context.operationCancelled()
      return
    }

    if (throwable != null) {
      this.logger.warn("Task failure: ", throwable)
      context.setState(
        OPDSState.Error(
          message = this.feedException(request, throwable),
          request = request
        )
      )
      this.taskFuture.completeExceptionally(throwable)
      return
    }

    return when (result) {
      null -> {
        context.setState(
          OPDSState.Error(
            message = this.feedException(request, NullPointerException()),
            request = request
          )
        )
        this.taskFuture.completeExceptionally(NullPointerException())
        Unit
      }

      is FeedLoaderFailedAuthentication -> {
        context.setState(
          OPDSState.Error(
            message = result,
            request = request
          )
        )
        Unit
      }

      is FeedLoaderFailedGeneral -> {
        context.setState(
          OPDSState.Error(
            message = result,
            request = request
          )
        )
        Unit
      }

      is FeedLoaderSuccess -> {
        val newState: OPDSStateHistoryParticipant =
          when (val feed = result.feed) {
            is Feed.FeedWithGroups -> {
              OPDSState.LoadedFeedWithGroups(request, feed)
            }

            is Feed.FeedWithoutGroups -> {
              OPDSState.LoadedFeedWithoutGroups(request, feed)
            }
          }

        context.setState(newState)
        Unit
      }
    }
  }

  private fun commandFetchExistingEntry(
    context: OPDSCmdContextType,
    request: OPDSClientRequest.ExistingEntry
  ) {
    try {
      context.setState(OPDSState.LoadedFeedEntry(request))
    } catch (e: Throwable) {
      this.taskFuture.completeExceptionally(e)
    } finally {
      this.taskFuture.complete(Unit)
    }
  }
}
