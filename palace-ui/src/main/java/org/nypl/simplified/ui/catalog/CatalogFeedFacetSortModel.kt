package org.nypl.simplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacetPseudoSorting
import org.nypl.simplified.feeds.api.FeedFacetSingle
import org.slf4j.LoggerFactory

object CatalogFeedFacetSortModel {
  private val logger =
    LoggerFactory.getLogger(CatalogFeedFacetSortModel::class.java)

  private val facetValues: MutableMap<CatalogPart, List<FeedFacetSingle>> =
    mutableMapOf()

  fun facetsSet(
    catalogPart: CatalogPart,
    sortBy: List<FeedFacetSingle>,
  ) {
    this.logger.debug("[{}] Creating sort model of size {}", catalogPart, sortBy.size)
    for (entry in sortBy) {
      this.logger.debug("[{}][{}]: Active: {}", catalogPart, entry.title, entry.isActive)
    }
    this.facetValues[catalogPart] = sortBy.toList()
  }

  fun facetSelected(catalogPart: CatalogPart): FeedFacetSingle? =
    this.facetValues[catalogPart]
      ?.find { s -> s.isActive }

  fun facetSelectedPseudo(catalogPart: CatalogPart): FeedFacetPseudoSorting? {
    val selected = this.facetSelected(catalogPart)
    if (selected is FeedFacetPseudoSorting) {
      return selected
    }
    return null
  }

  fun facetSelectedPseudoOrDefault(catalogPart: CatalogPart): FeedFacetPseudoSorting.SortBy {
    val selected = this.facetSelectedPseudo(catalogPart)
    if (selected != null) {
      return selected.sortBy
    }
    return FeedFacetPseudoSorting.SortBy.SORT_BY_TITLE
  }
}
