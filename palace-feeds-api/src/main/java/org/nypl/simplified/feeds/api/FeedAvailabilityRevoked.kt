package org.nypl.simplified.feeds.api

import java.net.URI
import java.time.OffsetDateTime

/**
 * The book has been revoked via DRM, but the server has yet to be notified.
 */
data class FeedAvailabilityRevoked(
  override val endDate: OffsetDateTime?,
  val revoke: URI,
) : FeedAvailability
