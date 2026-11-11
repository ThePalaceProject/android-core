package org.nypl.simplified.feeds.api

import java.net.URI

/**
 * A series associated with a feed entry.
 */
data class FeedSeries(
  val name: String,
  val position: Int?,
  val link: URI,
)
