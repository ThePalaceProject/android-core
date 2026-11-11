package org.nypl.simplified.feeds.api

import org.joda.time.DateTime

/**
 * The book is not available for borrowing but is available to place on hold.
 */
object FeedAvailabilityHoldable : FeedAvailability {
  override val endDate: DateTime? = null
}
