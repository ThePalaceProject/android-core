package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacets
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.thepalaceproject.theme.core.PalaceTabButtons

class CatalogFeedViewGroups(
  override val root: ViewGroup,
  private val onFacetSelected: (FeedFacet) -> Unit,
  private val onSearchSubmitted: (AccountID, FeedSearch, String) -> Unit,
  private val onToolbarBackPressed: () -> Unit,
  private val onToolbarLogoPressed: () -> Unit,
  private val screenSize: ScreenSizeInformationType,
  private val window: Window,
) : CatalogFeedView() {

  val swipeRefresh =
    this.root.findViewById<SwipeRefreshLayout>(R.id.catalogGroupsContentRefresh)
  val listView: RecyclerView =
    this.root.findViewById(R.id.catalogGroupsEntries)

  val tabsContainer: ViewGroup =
    this.root.findViewById(R.id.catalogGroupsTabsContainer)
  val tabs: RadioGroup =
    this.root.findViewById(R.id.catalogGroupsTabs)

  val catalogGroupsLogoContainer: ViewGroup =
    this.root.findViewById(R.id.catalogGroupsLogoContainer)
  val catalogGroupsLibraryLogo: ImageView =
    this.catalogGroupsLogoContainer.findViewById(R.id.catalogGroupsLibraryLogo)
  val catalogGroupsLibraryText: TextView =
    this.catalogGroupsLogoContainer.findViewById(R.id.catalogGroupsLibraryText)

  val toolbar: CatalogToolbar =
    CatalogToolbar(
      logo = this.root.findViewById(R.id.catalogGroupsToolbarLogo),
      logoTouch = this.root.findViewById(R.id.catalogGroupsToolbarLogoTouch),
      onSearchSubmitted = this.onSearchSubmitted,
      onToolbarBackPressed = this.onToolbarBackPressed,
      onToolbarLogoPressed = this.onToolbarLogoPressed,
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
    this.listView.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenSize.dpToPixels(16).toInt())
    )
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
        this.updateSelectedFacet(facetTabs = this.tabs, index = index)
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
      container: ViewGroup,
      layoutInflater: LayoutInflater,
      onFacetSelected: (FeedFacet) -> Unit,
      onSearchSubmitted: (AccountID, FeedSearch, String) -> Unit,
      onToolbarBackPressed: () -> Unit,
      onToolbarLogoPressed: () -> Unit,
      screenSize: ScreenSizeInformationType,
      window: Window,
    ): CatalogFeedViewGroups {
      return CatalogFeedViewGroups(
        onFacetSelected = onFacetSelected,
        onSearchSubmitted = onSearchSubmitted,
        onToolbarBackPressed = onToolbarBackPressed,
        onToolbarLogoPressed = onToolbarLogoPressed,
        root = layoutInflater.inflate(R.layout.catalog_feed_groups, container, true) as ViewGroup,
        screenSize = screenSize,
        window = window,
      )
    }
  }

  override fun clear() {
    this.root.isEnabled = false
    this.listView.adapter = null
  }
}
