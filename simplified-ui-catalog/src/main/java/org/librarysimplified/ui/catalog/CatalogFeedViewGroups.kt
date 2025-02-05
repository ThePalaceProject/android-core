package org.librarysimplified.ui.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacets
import org.nypl.simplified.feeds.api.FeedSearch
import org.thepalaceproject.theme.core.PalaceTabButtons

class CatalogFeedViewGroups(
  private val window: Window,
  override val root: ViewGroup,
  private val onFacetSelected: (FeedFacet) -> Unit,
  private val onSearchSubmitted: (FeedSearch, String) -> Unit
) : CatalogFeedView() {

  val swipeRefresh =
    this.root.findViewById<SwipeRefreshLayout>(R.id.catalogGroupsContentRefresh)
  val listView: RecyclerView =
    this.root.findViewById(R.id.catalogGroupsEntries)

  val tabsContainer: ViewGroup =
    this.root.findViewById(R.id.catalogGroupsTabsContainer)
  val tabs: RadioGroup =
    this.root.findViewById(R.id.catalogGroupsTabs)

  val toolbar: CatalogToolbar =
    CatalogToolbar(
      logo = this.root.findViewById(R.id.catalogGroupsToolbarLogo),
      logoTouch = this.root.findViewById(R.id.catalogGroupsToolbarLogoTouch),
      onSearchSubmitted = this.onSearchSubmitted,
      searchIcon = this.root.findViewById(R.id.catalogGroupsToolbarSearchIcon),
      searchText = this.root.findViewById(R.id.catalogGroupsToolbarSearchText),
      searchTouch = this.root.findViewById(R.id.catalogGroupsToolbarSearchIconTouch),
      text = this.root.findViewById(R.id.catalogGroupsToolbarText),
      window = this.window,
    )

  init {
    this.listView.layoutManager = LinearLayoutManager(this.root.context)
    this.listView.setHasFixedSize(true)
    (this.listView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.listView.setItemViewCacheSize(8)
  }

  fun configureTabs(
    feed: Feed.FeedWithGroups
  ) {
    val facetGroup =
      FeedFacets.findEntryPointFacetGroup(feed.facetsByGroup)

    if (facetGroup == null) {
      this.tabsContainer.visibility = View.GONE
      return
    }

    this.tabs.removeAllViews()
    PalaceTabButtons.configureGroup(
      context = this.root.context,
      group = this.tabs,
      count = facetGroup.size
    ) { index, button ->
      val facet = facetGroup[index]
      button.text = facet.title
      button.setOnClickListener {
        this.onFacetSelected(facet)
        updateSelectedFacet(facetTabs = this.tabs, index = index)
      }
    }

    /*
     * Uncheck all of the buttons, and then check the one that corresponds to the current
     * active facet.
     */

    this.tabs.clearCheck()

    for (index in 0 until facetGroup.size) {
      val facet = facetGroup[index]
      val button = this.tabs.getChildAt(index) as RadioButton

      if (facet.isActive) {
        this.tabs.check(button.id)
      }
    }
  }

  private fun updateSelectedFacet(
    facetTabs: RadioGroup,
    index: Int
  ) {
    facetTabs.clearCheck()
    val button = facetTabs.getChildAt(index) as RadioButton
    facetTabs.check(button.id)
  }

  companion object {
    fun create(
      window: Window,
      layoutInflater: LayoutInflater,
      container: ViewGroup,
      onFacetSelected: (FeedFacet) -> Unit,
      onSearchSubmitted: (FeedSearch, String) -> Unit
    ): CatalogFeedViewGroups {
      return CatalogFeedViewGroups(
        window = window,
        root = layoutInflater.inflate(R.layout.catalog_feed_groups, container, true) as ViewGroup,
        onFacetSelected = onFacetSelected,
        onSearchSubmitted = onSearchSubmitted,
      )
    }
  }
}
