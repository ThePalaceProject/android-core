package org.librarysimplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacet

object CatalogFacetModel {

  private var onSelectFacetCurrent: (FeedFacet) -> Unit = { }
  private var facetsCurrent : Map<String, List<FeedFacet>> = mapOf()

  fun setFacets(
    facetsByGroup: Map<String, List<FeedFacet>>,
    onSelectFacet: (FeedFacet) -> Unit
  ) {
    this.facetsCurrent = facetsByGroup.toMap()
    this.onSelectFacetCurrent = onSelectFacet
  }

  val onSelectFacet: (FeedFacet) -> Unit
    get() = this.onSelectFacetCurrent

  val facets : Map<String, List<FeedFacet>>
    get() = this.facetsCurrent

  val facetAsList: List<Pair<String, List<FeedFacet>>>
    get() = this.facets.entries
      .map { e -> Pair(e.key, e.value) }
      .sortedBy { e -> e.first }

}
