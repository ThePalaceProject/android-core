package org.nypl.simplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacet
import java.util.SortedMap

class CatalogFeedFacetFilterModel private constructor(
  val facets: List<FeedFacetModel>
) {
  companion object {
    fun create(
      groups: SortedMap<String, List<FeedFacet>>
    ): CatalogFeedFacetFilterModel {
      val facetModels = mutableListOf<FeedFacetModel>()
      for ((name, facets) in groups) {
        facetModels.add(
          FeedFacetModel(
            title = name,
            feedFacets = facets,
            expanded = true,
            selected = null
          )
        )
      }
      return CatalogFeedFacetFilterModel(facetModels.toList())
    }
  }

  class FeedFacetModel(
    val title: String,
    val feedFacets: List<FeedFacet>,
    var expanded: Boolean,
    var selected: Int?
  )
}
