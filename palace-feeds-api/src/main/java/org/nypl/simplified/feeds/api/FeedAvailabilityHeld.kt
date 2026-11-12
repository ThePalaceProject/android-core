package org.nypl.simplified.feeds.api

import java.net.URI
import java.time.OffsetDateTime

/**
 * The book is on hold.
 */
data class FeedAvailabilityHeld(
  val startDate: OffsetDateTime?,
  val position: Int?,
  override val endDate: OffsetDateTime?,
  val revoke: URI?,
) : FeedAvailability
