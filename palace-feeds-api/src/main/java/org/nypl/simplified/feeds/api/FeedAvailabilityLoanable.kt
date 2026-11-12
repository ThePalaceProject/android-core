package org.nypl.simplified.feeds.api

import java.time.OffsetDateTime

/**
 * The book is available for borrowing.
 */
object FeedAvailabilityLoanable : FeedAvailability {
  override val endDate: OffsetDateTime? = null
}
