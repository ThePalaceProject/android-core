package org.nypl.simplified.ui.catalog

import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.ui.catalog.CatalogFeedWithGroupsLaneViewHolder.LaneStyle.MAIN_GROUPED_FEED_LANE
import org.nypl.simplified.ui.catalog.CatalogFeedWithGroupsLaneViewHolder.LaneStyle.RELATED_BOOKS_LANE
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import java.net.URI

/**
 * A `ViewHolder` that represents a single swimlane within the [CatalogFeedWithGroupsAdapter].
 */
class CatalogFeedWithGroupsLaneViewHolder(
  private val parent: View,
  private val screenSize: ScreenSizeInformationType,
  private val coverLoader: BookCoverProviderType,
  private val laneStyle: LaneStyle,
  private val onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit
) : RecyclerView.ViewHolder(parent) {

  enum class LaneStyle {
    /**
     * The lane is appearing as an ordinary lane in a full grouped feed.
     */

    MAIN_GROUPED_FEED_LANE,

    /**
     * The lane is as a "related books" lane in the book details page. Therefore it should use
     * different padding and different text sizes.
     */

    RELATED_BOOKS_LANE
  }

  private val titleContainer =
    this.parent.findViewById<View>(R.id.feedLaneTitleContainer)
  private val title =
    this.parent.findViewById<TextView>(R.id.feedLaneTitle)
  private val more =
    this.parent.findViewById<TextView>(R.id.feedLaneMore)
  private val scrollView =
    this.parent.findViewById<RecyclerView>(R.id.feedLaneCoversScroll)

  init {
    this.scrollView.apply {
      this.setHasFixedSize(true)
      this.layoutManager = LinearLayoutManager(
        this.context, LinearLayoutManager.HORIZONTAL, false
      )

      this.addItemDecoration(
        SpaceItemDecoration(
          this.resources.getDimensionPixelSize(R.dimen.catalogFeedCoversSpace)
        )
      )
    }
  }

  fun bindTo(
    group: FeedGroup
  ) {
    val ignored = when (this.laneStyle) {
      MAIN_GROUPED_FEED_LANE -> {
        // Already configured correctly
      }

      RELATED_BOOKS_LANE -> {
        val newTitleLayout =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          )

        val marginH = this.screenSize.dpToPixels(32).toInt()
        val marginV = this.screenSize.dpToPixels(16).toInt()
        newTitleLayout.leftMargin = marginH
        newTitleLayout.rightMargin = marginH
        newTitleLayout.topMargin = marginV
        newTitleLayout.bottomMargin = marginV
        this.titleContainer.layoutParams = newTitleLayout
        this.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f)

        this.scrollView.setPadding(
          marginH,
          0,
          marginH,
          0
        )
      }
    }

    this.title.text = group.groupTitle
    this.title.setOnClickListener {
      this.onFeedSelected.invoke(group.account, group.groupTitle, group.groupURI)
    }
    this.more.setOnClickListener {
      this.onFeedSelected.invoke(group.account, group.groupTitle, group.groupURI)
    }

    /*
     * If the group is empty, there isn't much we can do.
     */

    if (group.groupEntries.isEmpty()) {
      this.scrollView.adapter = null
      return
    }

    /*
     * Populate our feed with our book covers
     */

    val catalogLaneAdapter =
      CatalogLaneAdapter(
        coverLoader = this.coverLoader,
        onBookSelected = this.onBookSelected
      )

    catalogLaneAdapter.submitList(group.groupEntries.filterIsInstance<FeedEntry.FeedEntryOPDS>())
    this.scrollView.adapter = catalogLaneAdapter
  }

  fun unbind() {
    this.scrollView.adapter = null
  }
}
