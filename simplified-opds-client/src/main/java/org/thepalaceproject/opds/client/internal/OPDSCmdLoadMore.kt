package org.thepalaceproject.opds.client.internal

import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSState.Error
import org.thepalaceproject.opds.client.OPDSState.Initial
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedEntry
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithGroups
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithoutGroups
import org.thepalaceproject.opds.client.OPDSState.Loading
import java.net.URI

internal class OPDSCmdLoadMore : OPDSCmd() {

  private val logger =
    LoggerFactory.getLogger(OPDSCmdExecuteRequest::class.java)

  override fun execute(
    context: OPDSCmdContextType
  ) {
    return when (val state = context.state) {
      is Error,
      is Initial,
      is Loading,
      is LoadedFeedEntry,
      is LoadedFeedWithGroups -> {
        this.taskFuture.complete(Unit)
        Unit
      }

      is LoadedFeedWithoutGroups -> {
        this.fetchNextFeed(context, state)
      }
    }
  }

  private fun fetchNextFeed(
    context: OPDSCmdContextType,
    state: LoadedFeedWithoutGroups
  ) {
    val next = state.feed.feedNext
    if (next == null) {
      this.logger.debug("Feed {} has no 'next'", state.request.uri)
      this.taskFuture.complete(Unit)
      return
    }

    val future0 =
      context.feedLoader.fetchURI(
        accountID = state.request.accountID,
        uri = next,
        credentials = state.request.credentials,
        method = state.request.method
      )

    future0.handle { result, throwable ->
      try {
        this.taskFuture.complete(this.processFeed(context, state, next, throwable, result))
      } catch (e: Throwable) {
        this.taskFuture.completeExceptionally(e)
      }
    }
  }

  private fun processFeed(
    context: OPDSCmdContextType,
    state: LoadedFeedWithoutGroups,
    uri: URI,
    throwable: Throwable?,
    result: FeedLoaderResult?
  ) {
    if (this.taskFuture.isCancelled) {
      context.operationCancelled()
      return
    }

    if (throwable != null) {
      this.logger.warn("Task failure: ", throwable)
      this.taskFuture.completeExceptionally(throwable)
      return
    }

    this.taskFuture.complete(
      when (result) {
        null -> {
          this.taskFuture.completeExceptionally(NullPointerException())
          Unit
        }

        is FeedLoaderFailedAuthentication -> {
          this.taskFuture.completeExceptionally(result.exception)
          Unit
        }

        is FeedLoaderFailedGeneral -> {
          this.taskFuture.completeExceptionally(result.exception)
          Unit
        }

        is FeedLoaderSuccess -> {
          when (val feed = result.feed) {
            is Feed.FeedWithGroups -> {
              this.logger.warn("Expected a feed without groups.")
              Unit
            }

            is Feed.FeedWithoutGroups -> {
              val newList = mutableListOf<FeedEntry>()
              newList.addAll(context.entriesUngrouped())
              newList.addAll(feed.entriesInOrder)
              val newFeed = state.feed.copy(feedNext = feed.feedNext)
              context.setEntriesUngrouped(newList.toList())
              context.setStateReplaceTop(state.copy(feed = newFeed))
              Unit
            }
          }
        }
      }
    )
  }
}
