package org.nypl.simplified.feeds.api

import java.net.URI
import java.time.OffsetDateTime

/**
 * The book is held and is ready to be checked out now.
 */
data class FeedAvailabilityHeldReady(
  override val endDate: OffsetDateTime?,
  val revoke: URI?,
) : FeedAvailability
