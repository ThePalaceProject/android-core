package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID

/**
 * A composite facet made up of a combination of facets from a single feed.
 * This type of facet requires a client to make multiple requests in order to
 * effectively end up at a single facet.
 */
data class FeedFacetComposite(
  val facets: List<FeedFacetData>,
  override val title: String,
  override val isActive: Boolean
) : FeedFacet {
  init {
    require(facets.isNotEmpty()) { "Composite facets must contain at least one facet." }
    require(facets.map { f -> f.accountID }.toSet().size == 1) {
      "The values that make up a composite facet must have the same account ID."
    }
  }

  val accountID: AccountID
    get() = facets[0].accountID
}
