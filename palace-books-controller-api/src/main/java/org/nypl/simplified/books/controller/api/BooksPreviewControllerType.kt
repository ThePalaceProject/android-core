package org.nypl.simplified.books.controller.api

import com.google.common.util.concurrent.FluentFuture
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.taskrecorder.api.TaskResult

/**
 * The books preview controller.
 */

interface BooksPreviewControllerType {

  /**
   * Handle the book preview to figure out what kind of preview we're dealing with
   *
   * @param entry Feed entry, used to get the format and the URI to download later
   */

  fun handleBookPreviewStatus(
    entry: FeedEntry.FeedEntryOPDS
  ): FluentFuture<TaskResult<*>>
}
