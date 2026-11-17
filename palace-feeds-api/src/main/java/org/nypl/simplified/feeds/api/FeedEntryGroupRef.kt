package org.nypl.simplified.feeds.api

import java.net.URI

/**
 * A reference to a group that a feed entry belongs to.
 */
data class FeedEntryGroupRef(
  val name: String,
  val uri: URI,
)
