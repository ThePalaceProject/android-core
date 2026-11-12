package org.nypl.simplified.feeds.api

import java.net.URI
import java.time.OffsetDateTime

/**
 * The book is loaned out to the user.
 */
data class FeedAvailabilityLoaned(
  val startDate: OffsetDateTime?,
  override val endDate: OffsetDateTime?,
  val revoke: URI?,
) : FeedAvailability
