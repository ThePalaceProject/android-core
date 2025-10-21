package org.nypl.simplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetSingle
import java.util.SortedMap

class CatalogFeedFacetFilterModel private constructor(
  val facets: List<FeedFacetModel>
) {
  fun createResultFacet(
    title: String
  ): FeedFacet {
    require(this.facets.isNotEmpty()) {
      "Catalog feed facet filter models cannot work with an empty list of facets."
    }

    return when (this.facets[0].feedFacets[0]) {
      is FeedFacet.FeedFacetOPDS12Single -> {
        val facetValues =
          this.facets
            .filter { f -> f.feedFacets.all { ff -> ff is FeedFacet.FeedFacetOPDS12Single } }
            .filter { f -> f.selected != null }
            .map { f -> f.feedFacets[f.selected!!] as FeedFacet.FeedFacetOPDS12Single }

        FeedFacet.FeedFacetOPDS12Composite(
          facetValues,
          title,
          true
        )
      }

      is FeedFacet.FeedFacetPseudo.FilteringForAccount -> {
        this.findSelectedFacetValue(this.facets[0])
      }

      is FeedFacet.FeedFacetPseudo.Sorting -> {
        this.findSelectedFacetValue(this.facets[0])
      }
    }
  }

  private fun findSelectedFacetValue(
    model: FeedFacetModel
  ): FeedFacet {
    val selected = model.selected
    return if (selected == null) {
      model.feedFacets[0]
    } else {
      model.feedFacets[selected]
    }
  }

  companion object {
    fun create(
      groups: SortedMap<String, List<FeedFacetSingle>>
    ): CatalogFeedFacetFilterModel {
      val facetModels = mutableListOf<FeedFacetModel>()
      for ((name, facets) in groups) {
        if (facets.isEmpty()) {
          continue
        }
        facetModels.add(
          FeedFacetModel(
            title = name,
            feedFacets = facets,
            expanded = true,
            selected = this.selectedIndexOf(facets)
          )
        )
      }
      return CatalogFeedFacetFilterModel(facetModels.toList())
    }

    private fun selectedIndexOf(
      facets: List<FeedFacetSingle>
    ): Int? {
      for ((index, facet) in facets.withIndex()) {
        if (facet.isActive) {
          return index
        }
      }
      return null
    }
  }

  class FeedFacetModel(
    val title: String,
    val feedFacets: List<FeedFacetSingle>,
    var expanded: Boolean,
    var selected: Int?
  ) {
    init {
      require(this.feedFacets.isNotEmpty()) {
        "Feed facet models cannot have an empty list of facet values."
      }
    }
  }
}
