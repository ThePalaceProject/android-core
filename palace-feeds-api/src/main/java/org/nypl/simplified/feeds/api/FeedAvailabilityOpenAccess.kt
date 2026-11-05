package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * The book is public domain / open access.
 */
data class FeedAvailabilityOpenAccess(
  override val endDate: DateTime?,
  val revoke: URI?
) : FeedAvailability
