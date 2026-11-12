package org.nypl.simplified.feeds.api

/**
 * A feed facet.
 */
sealed interface FeedFacet {
  val title: String
  val isActive: Boolean
}
