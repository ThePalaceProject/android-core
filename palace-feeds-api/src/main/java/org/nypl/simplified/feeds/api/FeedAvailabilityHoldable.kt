package org.nypl.simplified.feeds.api

import java.time.OffsetDateTime

/**
 * The book is not available for borrowing but is available to place on hold.
 */
object FeedAvailabilityHoldable : FeedAvailability {
  override val endDate: OffsetDateTime? = null
}
