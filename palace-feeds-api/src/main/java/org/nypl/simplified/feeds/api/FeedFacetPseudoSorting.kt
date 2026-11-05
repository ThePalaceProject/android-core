package org.nypl.simplified.feeds.api

/**
 * A sorting facet. Used for locally generated feeds.
 */
data class FeedFacetPseudoSorting(
  override val title: String,
  override val isActive: Boolean,
  val sortBy: SortBy
) : FeedFacetSingle {
  enum class SortBy {
    SORT_BY_AUTHOR,
    SORT_BY_TITLE
  }
}
