package org.nypl.simplified.feeds.api

import org.joda.time.DateTime

/**
 * The availability of a book in a feed.
 */
sealed interface FeedAvailability {
  /**
   * The date when the availability expires, if there is one.
   */
  val endDate: DateTime?
}
