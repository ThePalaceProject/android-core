package org.nypl.simplified.feeds.api

import com.io7m.jfunctional.Option
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetOPDS12Single.Companion.ENTRYPOINT_FACET_GROUP_TYPE
import java.util.Comparator

/**
 * Functions to process facets.
 */

object FeedFacets {

  private val specialFacets =
    mapOf(
      Pair("SORT BY", 0),
      Pair("AVAILABILITY", 1),
      Pair("DISTRIBUTOR", 2),
      Pair("COLLECTION NAME", 3)
    )

  /**
   * A special comparator for facets. We want to ensure that some facets are displayed
   * first. Facets that aren't "special" are simply in alphabetical order afterwards.
   */

  val facetComparator: Comparator<in String> = object : Comparator<String> {
    override fun compare(
      p0: String,
      p1: String
    ): Int {
      val p0u = p0.uppercase()
      val p1u = p1.uppercase()
      val special0 = specialFacets.containsKey(p0u)
      val special1 = specialFacets.containsKey(p1u)
      return if (special0) {
        if (special1) {
          val x0 = specialFacets[p0u] ?: 0
          val y0 = specialFacets[p1u] ?: 0
          Integer.compare(x0, y0)
        } else {
          1
        }
      } else {
        if (special1) {
          -1
        } else {
          p0u.compareTo(p1u)
        }
      }
    }
  }

  /**
   * Find the list of facets that represent an "entry point" group.
   *
   * @param feed The feed
   * @return A list of facets, or nothing if no entry point group is available
   */

  @JvmStatic
  fun findEntryPointFacetGroupForFeed(feed: Feed): List<FeedFacetSingle>? {
    return when (feed) {
      is Feed.FeedWithoutGroups ->
        findEntryPointFacetGroup(feed.facetsByGroup)
      is Feed.FeedWithGroups ->
        findEntryPointFacetGroup(feed.facetsByGroup)
    }
  }

  /**
   * Find the list of facets that represent an "entry point" group.
   *
   * @param groups The facets by group
   * @return A list of facets, or nothing if no entry point group is available
   */

  @JvmStatic
  fun findEntryPointFacetGroup(
    groups: Map<String, List<FeedFacetSingle>>
  ): List<FeedFacetSingle>? {
    for (groupName in groups.keys) {
      val facets = groups[groupName].orEmpty()
      if (facets.isNotEmpty()) {
        val facet = facets.first()
        if (facetIsEntryPointTyped(facet)) {
          return facets
        }
      }
    }

    return null
  }

  /**
   * @return `true` if all of the facet groups are "entry point" type facets
   */

  @JvmStatic
  fun facetGroupsAreAllEntryPoints(facetGroups: Map<String, List<FeedFacetSingle>>): Boolean {
    return facetGroups.all { entry -> facetGroupIsEntryPointTyped(entry.value) }
  }

  /**
   * @return `true` if the given facet group is "entry point" typed
   */

  @JvmStatic
  fun facetGroupIsEntryPointTyped(facets: List<FeedFacetSingle>): Boolean {
    return facets.all { facet -> facetIsEntryPointTyped(facet) }
  }

  /**
   * @return `true` if the given facet is "entry point" typed
   */

  @JvmStatic
  fun facetIsEntryPointTyped(facet: FeedFacetSingle): Boolean {
    return when (facet) {
      is FeedFacet.FeedFacetOPDS12Single ->
        facet.opdsFacet.groupType == Option.some(ENTRYPOINT_FACET_GROUP_TYPE)
      is FeedFacet.FeedFacetPseudo ->
        false
    }
  }

  @JvmStatic
  fun isIgnoredFacet(value: Map.Entry<String, List<FeedFacetSingle>>): Boolean {
    return value.key.uppercase() == "COLLECTION"
  }
}
