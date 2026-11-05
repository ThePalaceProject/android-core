package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * The book is loaned out to the user.
 */
data class FeedAvailabilityLoaned(
  val startDate: DateTime?,
  override val endDate: DateTime?,
  val revoke: URI?
) : FeedAvailability
