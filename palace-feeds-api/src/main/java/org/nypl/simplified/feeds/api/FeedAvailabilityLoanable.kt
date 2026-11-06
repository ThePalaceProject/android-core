package org.nypl.simplified.feeds.api

import org.joda.time.DateTime

/**
 * The book is available for borrowing.
 */
object FeedAvailabilityLoanable  : FeedAvailability {
  override val endDate: DateTime? = null
}
