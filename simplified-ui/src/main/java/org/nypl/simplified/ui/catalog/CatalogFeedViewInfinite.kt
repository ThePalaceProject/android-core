package org.nypl.simplified.ui.catalog

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedFacets
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.thepalaceproject.theme.core.PalaceTabButtons
import java.util.SortedMap
import java.util.TreeMap

class CatalogFeedViewInfinite(
  override val root: ViewGroup,
  private val layoutInflater: LayoutInflater,
  private val catalogPart: CatalogPart,
  private val onFacetSelected: (FeedFacet) -> Unit,
  private val onSearchSubmitted: (AccountID, FeedSearch, String) -> Unit,
  private val onCatalogLogoClicked: () -> Unit,
  private val onToolbarBackPressed: () -> Unit,
  private val onToolbarLogoPressed: () -> Unit,
  private val window: Window,
) : CatalogFeedView() {

  val swipeRefresh =
    this.root.findViewById<SwipeRefreshLayout>(R.id.catalogFeedContentRefresh)
  val listView: RecyclerView =
    this.root.findViewById(R.id.catalogFeedEntries)

  val catalogFeedContentHeader: ViewGroup =
    this.root.findViewById(R.id.catalogFeedContentHeader)
  val catalogFeedHeaderTabs: RadioGroup =
    this.root.findViewById(R.id.catalogFeedHeaderTabs)
  val catalogFeedHeaderFacets: LinearLayout =
    this.root.findViewById(R.id.catalogFeedHeaderFacets)

  val catalogFeedHeaderFacetsSort: LinearLayout =
    this.catalogFeedHeaderFacets.findViewById(R.id.catalogFeedFacetSort)
  val catalogFeedHeaderFacetsSortText: TextView =
    this.catalogFeedHeaderFacetsSort.findViewById(R.id.catalogFeedFacetSortText)
  val catalogFeedHeaderFacetsFilter: LinearLayout =
    this.catalogFeedHeaderFacets.findViewById(R.id.catalogFeedFacetFilter)
  val catalogFeedHeaderFacetsFilterText: TextView =
    this.catalogFeedHeaderFacetsFilter.findViewById(R.id.catalogFeedFacetFilterText)

  val catalogFeedEmptyMessage: TextView =
    this.root.findViewById(R.id.catalogFeedEmptyMessage)

  val toolbar: CatalogToolbar =
    CatalogToolbar(
      logo = this.root.findViewById(R.id.catalogFeedToolbarLogo),
      logoTouch = this.root.findViewById(R.id.catalogFeedToolbarLogoTouch),
      onSearchSubmitted = this.onSearchSubmitted,
      onToolbarBackPressed = this.onToolbarBackPressed,
      onToolbarLogoPressed = this.onToolbarLogoPressed,
      searchIcon = this.root.findViewById(R.id.catalogFeedToolbarSearchIcon),
      searchText = this.root.findViewById(R.id.catalogFeedToolbarSearchText),
      searchTouch = this.root.findViewById(R.id.catalogFeedToolbarSearchIconTouch),
      text = this.root.findViewById(R.id.catalogFeedToolbarText),
      textContainer = this.root.findViewById(R.id.catalogFeedToolbarTextContainer),
      textIconView = this.root.findViewById(R.id.catalogFeedToolbarTextLibraryIcon),
      window = this.window,
    )

  init {
    this.listView.layoutManager = GridLayoutManager(this.root.context, this.columnCount())
    this.listView.setHasFixedSize(true)
    (this.listView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.listView.setItemViewCacheSize(8)
  }

  private fun columnCount(): Int {
    val orientation = this.root.resources.configuration.orientation
    return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      2
    } else {
      1
    }
  }

  companion object {

    private const val FACET_SORTING_NAME =
      "SORT BY"

    fun create(
      window: Window,
      layoutInflater: LayoutInflater,
      container: ViewGroup,
      catalogPart: CatalogPart,
      onFacetSelected: (FeedFacet) -> Unit,
      onSearchSubmitted: (AccountID, FeedSearch, String) -> Unit,
      onToolbarBackPressed: () -> Unit,
      onToolbarLogoPressed: () -> Unit,
      onCatalogLogoClicked: () -> Unit
    ): CatalogFeedViewInfinite {
      return CatalogFeedViewInfinite(
        catalogPart = catalogPart,
        onFacetSelected = onFacetSelected,
        onSearchSubmitted = onSearchSubmitted,
        onToolbarBackPressed = onToolbarBackPressed,
        onToolbarLogoPressed = onToolbarLogoPressed,
        root = layoutInflater.inflate(R.layout.catalog_feed_infinite, container, true) as ViewGroup,
        layoutInflater = layoutInflater,
        window = window,
        onCatalogLogoClicked = onCatalogLogoClicked
      )
    }
  }

  fun configureFacets(
    screen: ScreenSizeInformationType,
    feed: Feed.FeedWithoutGroups
  ) {
    /*
     * If the facet groups are empty, hide the header entirely.
     */

    val facetsByGroup = feed.facetsByGroup
    if (facetsByGroup.isEmpty()) {
      this.catalogFeedContentHeader.visibility = View.GONE
      return
    }

    /*
     * If one of the groups is an entry point, display it as a set of tabs. Otherwise, hide
     * the tab layout entirely.
     */

    this.configureFacetTabs(
      facetGroup = FeedFacets.findEntryPointFacetGroup(facetsByGroup),
      facetTabs = this.catalogFeedHeaderTabs
    )

    /*
     * Otherwise, for each remaining non-entrypoint facet group, show a UI allowing
     * the selection of individual facets. If there are no remaining groups, hide the button
     * bar.
     */

    val remainingGroups =
      facetsByGroup
        .filter { entry -> !FeedFacets.facetGroupIsEntryPointTyped(entry.value) }
        .filter { entry -> !FeedFacets.isIgnoredFacet(entry) }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    if (remainingGroups.isEmpty()) {
      this.catalogFeedHeaderFacets.visibility = View.GONE
      return
    }

    this.configureFacetsSorting(remainingGroups)
    this.configureFacetsFiltering(screen, remainingGroups)
  }

  /**
   * Set up the filtering facets. This is a fairly complex dialog box that allows for turning
   * on and off individual filtering facets and then submitting a request at the end with the
   * combination of options.
   */

  private fun configureFacetsFiltering(
    screen: ScreenSizeInformationType,
    groups: SortedMap<String, List<FeedFacetSingle>>
  ) {
    val withoutSortBy = TreeMap<String, List<FeedFacetSingle>>(String.CASE_INSENSITIVE_ORDER)
    withoutSortBy.putAll(groups)
    withoutSortBy.remove(FACET_SORTING_NAME)

    if (withoutSortBy.isEmpty()) {
      this.catalogFeedHeaderFacetsFilter.visibility = View.GONE
      return
    }

    val text =
      this.root.context.getString(R.string.catalogFacetsFilter, withoutSortBy.size)

    this.catalogFeedHeaderFacetsFilterText.text = text
    this.catalogFeedHeaderFacetsFilter.setOnClickListener {
      CatalogFeedFacetFilterModels.createNew(withoutSortBy)

      val view =
        this.layoutInflater.inflate(R.layout.catalog_facet_filters, null)
      val facetListView =
        view.findViewById<RecyclerView>(R.id.catalogFacetsFilterList)
      val facetApply =
        view.findViewById<Button>(R.id.catalogFacetsFilterApply)
      val adapter =
        CatalogFeedFacetFilterAdapter()

      facetApply.setOnClickListener {
        this.onFacetSelected.invoke(
          CatalogFeedFacetFilterModels.filterModel.createResultFacet(
            this.root.resources.getString(R.string.catalogResults)
          )
        )
      }

      facetListView.adapter = adapter
      facetListView.layoutManager = LinearLayoutManager(this.root.context)
      (facetListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      facetListView.setItemViewCacheSize(8)

      adapter.submitList(CatalogFeedFacetFilterModels.filterModel.facets)

      val dialogBuilder = MaterialAlertDialogBuilder(this.root.context)
      dialogBuilder.setView(view)
      val dialog = dialogBuilder.create()
      dialog.show()

      /*
       * Resize the dialog to 80% of the screen size.
       */

      val width = screen.widthPixels * 0.8
      val height = screen.heightPixels * 0.8
      dialog.window?.setLayout(width.toInt(), height.toInt())
    }
  }

  /**
   * Set up the sorting facet. This is simply a dialog with a radio group. Each button in
   * the radio group selects a single sorting facet.
   */

  private fun configureFacetsSorting(
    groups: SortedMap<String, List<FeedFacetSingle>>
  ) {
    if (!groups.containsKey(FACET_SORTING_NAME)) {
      this.catalogFeedHeaderFacetsSort.visibility = View.GONE
      return
    }

    val sortBy =
      groups[FACET_SORTING_NAME]!!
    val selected =
      sortBy.find { facet -> facet.isActive }
    this.catalogFeedHeaderFacetsSortText.text =
      selected?.title ?: ""

    this.catalogFeedHeaderFacetsSort.setOnClickListener {
      val view =
        this.layoutInflater.inflate(R.layout.catalog_facet_sort_by, null)
      val group =
        view.findViewById<RadioGroup>(R.id.catalogFacetDialogSortByGroup)

      var checked = 0
      sortBy.forEachIndexed { index, facet ->
        val button = RadioButton(this.root.context)
        button.setOnClickListener {
          this.onFacetSelected(facet)
        }
        button.id = index
        button.text = facet.title
        if (facet.isActive) {
          checked = index
        }
        group.addView(button)
      }
      group.check(checked)

      val dialogBuilder = MaterialAlertDialogBuilder(this.root.context)
      dialogBuilder.setView(view)
      val dialog = dialogBuilder.create()
      dialog.show()
    }
  }

  private fun configureFacetTabs(
    facetGroup: List<FeedFacet>?,
    facetTabs: RadioGroup
  ) {
    if (facetGroup == null) {
      facetTabs.visibility = View.GONE
      return
    }

    facetTabs.removeAllViews()
    PalaceTabButtons.configureGroup(
      context = this.root.context,
      group = facetTabs,
      count = facetGroup.size
    ) { index, button ->
      val facet = facetGroup[index]
      button.text = facet.title
      button.setOnClickListener {
        this.onFacetSelected(facet)
        this.updateSelectedFacet(facetTabs = facetTabs, index = index)
      }
    }

    /*
     * Uncheck all of the buttons, and then check the one that corresponds to the current
     * active facet.
     */

    facetTabs.clearCheck()

    for (index in 0 until facetGroup.size) {
      val facet = facetGroup[index]
      val button = facetTabs.getChildAt(index) as RadioButton

      if (facet.isActive) {
        facetTabs.check(button.id)
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

  fun configureListVisibility(
    itemCount: Int,
    onEmptyMessage: String
  ) {
    if (itemCount == 0) {
      this.listView.visibility = View.INVISIBLE
      this.catalogFeedEmptyMessage.visibility = View.VISIBLE
      this.catalogFeedEmptyMessage.text = onEmptyMessage
    } else {
      this.listView.visibility = View.VISIBLE
      this.catalogFeedEmptyMessage.visibility = View.INVISIBLE
    }
  }

  override fun startFocus() {
    this.toolbar.requestFocus()
  }

  override fun clear() {
    this.root.isEnabled = false
    this.listView.adapter = null
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    (this.listView.layoutManager as? GridLayoutManager)?.spanCount = this.columnCount()
  }
}
