package org.thepalaceproject.opds.client.internal

import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSState
import java.net.URI

internal class OPDSCmdLoadMore : OPDSCmd() {

  private val logger =
    LoggerFactory.getLogger(OPDSCmdExecuteRequest::class.java)

  override fun execute(
    context: OPDSCmdContextType
  ) {
    this.logger.debug("Loading more feed.")

    return when (val state = context.state) {
      is OPDSState.Error,
      OPDSState.Initial,
      is OPDSState.Loading,
      is OPDSState.LoadedFeedEntry,
      is OPDSState.LoadedFeedWithGroups -> {
        this.taskFuture.complete(Unit)
        Unit
      }

      is OPDSState.LoadedFeedWithoutGroups -> {
        this.fetchNextFeed(context, state)
      }
    }
  }

  private fun fetchNextFeed(
    context: OPDSCmdContextType,
    state: OPDSState.LoadedFeedWithoutGroups
  ) {
    val next = state.feed.feedNext
    if (next == null) {
      this.logger.debug("Feed {} has no 'next'", state.request.uri)
      this.taskFuture.complete(Unit)
      return
    }

    this.logger.debug("Loading feed {}", next)
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
    state: OPDSState.LoadedFeedWithoutGroups,
    uri: URI,
    throwable: Throwable?,
    result: FeedLoaderResult?
  ) {
    if (this.taskFuture.isCancelled) {
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
              this.logger.debug("Constructed new list of {} entries", newList.size)
              this.logger.debug("Next feed is now: {}", newFeed.feedNext)
              context.setEntriesUngroupedSource(newList.toList())
              context.setState(state.copy(feed = newFeed))
              Unit
            }
          }
        }
      }
    )
  }
}
