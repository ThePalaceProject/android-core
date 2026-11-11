package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * The book has been revoked via DRM, but the server has yet to be notified.
 */
data class FeedAvailabilityRevoked(
  override val endDate: DateTime?,
  val revoke: URI,
) : FeedAvailability
