package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * The book is on hold.
 */
data class FeedAvailabilityHeld(
  val startDate: DateTime?,
  val position: Int?,
  override val endDate: DateTime?,
  val revoke: URI?,
) : FeedAvailability
