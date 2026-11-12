package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Convenience functions for fetching and parsing feeds.
 */
object FeedLoading {
  /**
   * Synchronously load a URI, returning the first usable entry in the feed.
   *
   * @param feedLoader The feed loader that will be used
   * @param taskRecorder A task recorder used to record the operation steps
   * @param accountID The account that owns the feed
   * @param uri The target URI
   * @param timeout The timeout value
   * @param method The HTTP request method
   */
  fun loadSingleEntryFeed(
    feedLoader: FeedLoaderType,
    taskRecorder: TaskRecorderType,
    accountID: AccountID,
    uri: URI,
    timeout: Pair<Long, TimeUnit>,
    method: String = "GET",
  ): FeedEntryBook {
    taskRecorder.beginNewStep("Fetching feed...")

    val feedResult =
      feedLoader
        .fetchURI(
          accountID = accountID,
          uri = uri,
          credentials = null,
          method = method,
        ).get(timeout.first, timeout.second)

    return when (feedResult) {
      is FeedLoaderFailedAuthentication -> {
        taskRecorder.currentStepFailed(
          message = feedResult.message,
          errorCode = "feedAuthentication",
          exception = feedResult.exception,
          extraMessages = listOf(),
        )
        throw feedResult.exception
      }

      is FeedLoaderFailedGeneral -> {
        taskRecorder.currentStepFailed(
          message = feedResult.message,
          errorCode = "feedFailed",
          exception = feedResult.exception,
          extraMessages = listOf(),
        )
        throw feedResult.exception
      }

      is FeedLoaderSuccess -> {
        taskRecorder.currentStepSucceeded("Feed retrieved and parsed.")
        taskRecorder.beginNewStep("Finding feed entry...")

        when (val feed = feedResult.feed) {
          is FeedData -> {
            this.checkEntry(taskRecorder, findFirst(feed.entries))
          }

          is FeedDataWithGroups -> {
            this.checkEntry(taskRecorder, findFirstInGroups(feed.groups))
          }
        }
      }
    }
  }

  private fun checkEntry(
    taskRecorder: TaskRecorderType,
    entry: FeedEntryBook?,
  ): FeedEntryBook {
    if (entry != null) {
      taskRecorder.currentStepSucceeded("Found feed entry.")
      return entry
    } else {
      val message = "Expected a feed containing at least one entry"
      val exception = IllegalArgumentException(message)
      taskRecorder.currentStepFailed(
        message = message,
        errorCode = "feedUnsuitable",
        exception = exception,
        extraMessages = listOf(),
      )
      throw exception
    }
  }

  private fun findFirst(feedEntries: List<FeedEntry>): FeedEntryBook? {
    for (entry in feedEntries) {
      when (entry) {
        is FeedEntryCorrupt -> Unit
        is FeedEntryBook -> return entry
      }
    }
    return null
  }

  private fun findFirstInGroups(feedGroups: List<FeedGroup>): FeedEntryBook? {
    for (group in feedGroups) {
      for (entry in group.groupEntries) {
        when (entry) {
          is FeedEntryCorrupt -> Unit
          is FeedEntryBook -> return entry
        }
      }
    }
    return null
  }
}
