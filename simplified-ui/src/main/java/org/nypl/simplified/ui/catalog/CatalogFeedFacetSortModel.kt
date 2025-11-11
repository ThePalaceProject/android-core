package org.nypl.simplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacet
import org.slf4j.LoggerFactory

object CatalogFeedFacetSortModel {

  private val logger =
    LoggerFactory.getLogger(CatalogFeedFacetSortModel::class.java)

  private val facetValues: MutableMap<CatalogPart, List<FeedFacet.FeedFacetSingle>> =
    mutableMapOf()

  fun facetsSet(
    catalogPart: CatalogPart,
    sortBy: List<FeedFacet.FeedFacetSingle>
  ) {
    this.logger.debug("[{}] Creating sort model of size {}", catalogPart, sortBy.size)
    for (entry in sortBy) {
      this.logger.debug("[{}][{}]: Active: {}", catalogPart, entry.title, entry.isActive)
    }
    this.facetValues[catalogPart] = sortBy.toList()
  }

  fun facetSelected(
    catalogPart: CatalogPart
  ): FeedFacet.FeedFacetSingle? {
    return this.facetValues[catalogPart]
      ?.find { s -> s.isActive }
  }

  fun facetSelectedPseudo(
    catalogPart: CatalogPart
  ): FeedFacet.FeedFacetPseudo.Sorting? {
    val selected = this.facetSelected(catalogPart)
    if (selected is FeedFacet.FeedFacetPseudo.Sorting) {
      return selected
    }
    return null
  }

  fun facetSelectedPseudoOrDefault(
    catalogPart: CatalogPart
  ): FeedFacet.FeedFacetPseudo.Sorting.SortBy {
    val selected = this.facetSelectedPseudo(catalogPart)
    if (selected != null) {
      return selected.sortBy
    }
    return FeedFacet.FeedFacetPseudo.Sorting.SortBy.SORT_BY_TITLE
  }
}
