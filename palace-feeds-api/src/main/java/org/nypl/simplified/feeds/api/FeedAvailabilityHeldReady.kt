package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * The book is held and is ready to be checked out now.
 */
data class FeedAvailabilityHeldReady(
  override val endDate: DateTime?,
  val revoke: URI?
) : FeedAvailability
