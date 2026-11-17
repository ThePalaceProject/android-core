package org.nypl.simplified.feeds.api

import java.net.URI
import java.time.OffsetDateTime

/**
 * The book is public domain / open access.
 */
data class FeedAvailabilityOpenAccess(
  override val endDate: OffsetDateTime?,
  val revoke: URI?,
) : FeedAvailability
