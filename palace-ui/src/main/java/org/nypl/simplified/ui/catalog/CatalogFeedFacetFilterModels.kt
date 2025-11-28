package org.nypl.simplified.ui.catalog

import org.nypl.simplified.feeds.api.FeedFacet
import org.slf4j.LoggerFactory
import java.util.SortedMap

object CatalogFeedFacetFilterModels {

  private val logger =
    LoggerFactory.getLogger(CatalogFeedFacetFilterModels::class.java)

  private var INSTANCE =
    CatalogFeedFacetFilterModel.create(sortedMapOf())

  val filterModel: CatalogFeedFacetFilterModel
    get() = this.INSTANCE

  fun createNew(
    facets: SortedMap<String, List<FeedFacet.FeedFacetSingle>>
  ) {
    this.logCreation(facets)
    this.INSTANCE = CatalogFeedFacetFilterModel.create(facets)
  }

  private fun logCreation(
    facets: SortedMap<String, List<FeedFacet.FeedFacetSingle>>
  ) {
    try {
      this.logger.debug("Creating a new facet filter model of size {}.", facets.size)
      for (e in facets.entries) {
        val selected = e.value.find { f -> f.isActive } ?: e.value.firstOrNull()
        this.logger.debug("Initial [{}]: {}", e.key, selected?.title)
      }
    } catch (e: Throwable) {
      // Ignore.
    }
  }
}
