package org.nypl.simplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedFacetComposite
import org.nypl.simplified.feeds.api.FeedFacetPseudoFiltering
import org.nypl.simplified.feeds.api.FeedFacetPseudoSorting
import org.slf4j.LoggerFactory
import java.util.SortedMap

class CatalogFeedFacetFilterModel private constructor(
  val facets: List<FeedFacetModel>
) {
  object CatalogFeedFacetFilterModelLog {
    internal val logger =
      LoggerFactory.getLogger(FeedFacetModel::class.java)
  }

  fun createResultFacet(title: String): FeedFacet {
    require(this.facets.isNotEmpty()) {
      "Catalog feed facet filter models cannot work with an empty list of facets."
    }

    return when (this.facets[0].feedFacets[0]) {
      is FeedFacetData -> {
        val facetValues =
          this.facets
            .filter { f -> f.feedFacets.all { ff -> ff is FeedFacetData } }
            .map { f -> f.feedFacets[f.selectedIndex] as FeedFacetData }

        for (value in facetValues) {
          CatalogFeedFacetFilterModelLog.logger.debug(
            "Result: Facet[{}]: Value '{}'",
            value.group,
            value.title,
          )
        }

        FeedFacetComposite(
          facetValues,
          title,
          true
        )
      }

      is FeedFacetPseudoFiltering -> {
        this.findSelectedFacetValue(this.facets[0])
      }

      is FeedFacetPseudoSorting -> {
        throw IllegalStateException("Sorting facets are not permitted.")
      }
    }
  }

  private fun findSelectedFacetValue(model: FeedFacetModel): FeedFacet = model.feedFacets[model.selectedIndex]

  companion object {
    fun create(groups: SortedMap<String, List<FeedFacetSingle>>): CatalogFeedFacetFilterModel {
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
            selected = this.initiallySelectedIndexOf(facets)
          )
        )
      }
      return CatalogFeedFacetFilterModel(facetModels.toList())
    }

    private fun initiallySelectedIndexOf(facets: List<FeedFacetSingle>): Int {
      for ((index, facet) in facets.withIndex()) {
        if (facet.isActive) {
          return index
        }
      }
      return 0
    }
  }

  class FeedFacetModel(
    val title: String,
    val feedFacets: List<FeedFacetSingle>,
    var expanded: Boolean,
    private var selected: Int
  ) {
    companion object {
      private val logger =
        LoggerFactory.getLogger(FeedFacetModel::class.java)
    }

    init {
      require(this.feedFacets.isNotEmpty()) {
        "Feed facet models cannot have an empty list of facet values."
      }
    }

    val selectedIndex: Int
      get() = this.selected

    fun setSelected(index: Int) {
      logger.debug(
        "Facet[{}]: Value '{}' (index {})",
        this.title,
        this.feedFacets[index].title,
        index,
      )
      this.selected = index
    }
  }
}
