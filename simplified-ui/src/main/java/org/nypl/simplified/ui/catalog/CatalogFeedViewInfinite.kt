package org.nypl.simplified.ui.catalog

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.TEXT_ALIGNMENT_TEXT_END
import android.view.ViewGroup
import android.view.Window
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.librarysimplified.ui.R
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacets
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.thepalaceproject.theme.core.PalaceTabButtons

class CatalogFeedViewInfinite(
  override val root: ViewGroup,
  private val onFacetSelected: (FeedFacet) -> Unit,
  private val onSearchSubmitted: (FeedSearch, String) -> Unit,
  private val onToolbarBackPressed: () -> Unit,
  private val onToolbarLogoPressed: () -> Unit,
  private val window: Window,
) : CatalogFeedView() {

  val swipeRefresh =
    this.root.findViewById<SwipeRefreshLayout>(R.id.catalogFeedContentRefresh)
  val listView: RecyclerView =
    this.root.findViewById(R.id.catalogFeedEntries)

  val catalogFeedLayout: ViewGroup =
    this.root.findViewById(R.id.catalogFeedLogoContainer)
  val catalogFeedLibraryLogo: ImageView =
    this.catalogFeedLayout.findViewById(R.id.catalogFeedLibraryLogo)
  val catalogFeedLibraryText: TextView =
    this.catalogFeedLayout.findViewById(R.id.catalogFeedLibraryText)

  val catalogFeedContentHeader: ViewGroup =
    this.root.findViewById(R.id.catalogFeedContentHeader)
  val catalogFeedHeaderTabs: RadioGroup =
    this.root.findViewById(R.id.catalogFeedHeaderTabs)
  val catalogFeedHeaderFacetsScroll: HorizontalScrollView =
    this.root.findViewById(R.id.catalogFeedHeaderFacetsScroll)
  val catalogFeedHeaderFacets: LinearLayout =
    this.root.findViewById(R.id.catalogFeedHeaderFacets)

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
      window = this.window,
    )

  init {
    this.listView.layoutManager = LinearLayoutManager(this.root.context)
    this.listView.setHasFixedSize(true)
    (this.listView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.listView.setItemViewCacheSize(8)

    this.catalogFeedHeaderFacets.removeAllViews()
  }

  companion object {
    fun create(
      window: Window,
      layoutInflater: LayoutInflater,
      container: ViewGroup,
      onFacetSelected: (FeedFacet) -> Unit,
      onSearchSubmitted: (FeedSearch, String) -> Unit,
      onToolbarBackPressed: () -> Unit,
      onToolbarLogoPressed: () -> Unit
    ): CatalogFeedViewInfinite {
      return CatalogFeedViewInfinite(
        onFacetSelected = onFacetSelected,
        onSearchSubmitted = onSearchSubmitted,
        onToolbarBackPressed = onToolbarBackPressed,
        onToolbarLogoPressed = onToolbarLogoPressed,
        root = layoutInflater.inflate(R.layout.catalog_feed_infinite, container, true) as ViewGroup,
        window = window,
      )
    }
  }

  fun configureFacets(
    screen: ScreenSizeInformationType,
    feed: Feed.FeedWithoutGroups,
    sortFacets: Boolean
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
     * Otherwise, for each remaining non-entrypoint facet group, show a drop-down menu allowing
     * the selection of individual facets. If there are no remaining groups, hide the button
     * bar.
     */

    val remainingGroups =
      facetsByGroup
        .filter { entry -> !FeedFacets.facetGroupIsEntryPointTyped(entry.value) }

    if (remainingGroups.isEmpty()) {
      this.catalogFeedHeaderFacetsScroll.visibility = View.GONE
      return
    }

    val buttonLayoutParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

    val textLayoutParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

    textLayoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL

    val spacerLayoutParams =
      LinearLayout.LayoutParams(
        screen.dpToPixels(8).toInt(),
        LinearLayout.LayoutParams.MATCH_PARENT
      )

    val sortedNames = if (sortFacets) {
      remainingGroups.keys.sorted()
    } else {
      remainingGroups.keys
    }

    val context = this.root.context
    this.catalogFeedHeaderFacets.removeAllViews()
    sortedNames.forEach { groupName ->
      val group = remainingGroups.getValue(groupName)
      if (FeedFacets.facetGroupIsEntryPointTyped(group)) {
        return@forEach
      }

      val button = MaterialButton(context)
      val buttonLabel = AppCompatTextView(context)
      val spaceStart = Space(context)
      val spaceMiddle = Space(context)
      val spaceEnd = Space(context)

      val active =
        group.find { facet -> facet.isActive }
          ?: group.firstOrNull()

      button.id = View.generateViewId()
      button.layoutParams = buttonLayoutParams
      button.text = active?.title
      button.ellipsize = TextUtils.TruncateAt.END
      button.setOnClickListener {
        this.showFacetSelectDialog(groupName, group)
      }

      spaceStart.layoutParams = spacerLayoutParams
      spaceMiddle.layoutParams = spacerLayoutParams
      spaceEnd.layoutParams = spacerLayoutParams

      buttonLabel.layoutParams = textLayoutParams
      buttonLabel.text = "$groupName: "
      buttonLabel.labelFor = button.id
      buttonLabel.maxLines = 1
      buttonLabel.ellipsize = TextUtils.TruncateAt.END
      buttonLabel.textAlignment = TEXT_ALIGNMENT_TEXT_END
      buttonLabel.gravity = Gravity.END or Gravity.CENTER_VERTICAL

      this.catalogFeedHeaderFacets.addView(spaceStart)
      this.catalogFeedHeaderFacets.addView(buttonLabel)
      this.catalogFeedHeaderFacets.addView(spaceMiddle)
      this.catalogFeedHeaderFacets.addView(button)
      this.catalogFeedHeaderFacets.addView(spaceEnd)
    }

    this.catalogFeedHeaderFacetsScroll.scrollTo(0, 0)
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

  private fun showFacetSelectDialog(
    groupName: String,
    group: List<FeedFacet>
  ) {
    val choices = group.sortedBy { it.title }
    val names = choices.map { it.title }.toTypedArray()
    val checkedItem = choices.indexOfFirst { it.isActive }

    // Build the dialog
    val alertBuilder = MaterialAlertDialogBuilder(this.root.context)
    alertBuilder.setTitle(groupName)
    alertBuilder.setSingleChoiceItems(names, checkedItem) { dialog, checked ->
      val selected = choices[checked]
      this.onFacetSelected(selected)
      dialog.dismiss()
    }
    alertBuilder.create().show()
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

  override fun clear() {
    this.root.isEnabled = false
    this.listView.adapter = null
  }
}
