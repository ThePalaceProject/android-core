package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.ui.catalog.CatalogFeedWithGroupsLaneViewHolder.LaneStyle
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import java.net.URI

/**
 * An adapter that produces swimlanes for feeds that have groups.
 */

class CatalogFeedWithGroupsAdapter(
  private val covers: BookCoverProviderType,
  private val laneStyle: LaneStyle,
  private val screenSize: ScreenSizeInformationType,
  private val onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit
) : ListAdapter<FeedGroup, CatalogFeedWithGroupsLaneViewHolder>(diffCallback) {

  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<FeedGroup>() {
        override fun areContentsTheSame(
          oldItem: FeedGroup,
          newItem: FeedGroup
        ): Boolean {
          if (oldItem.groupEntries.size == newItem.groupEntries.size) {
            for (index in 0 until oldItem.groupEntries.size) {
              if (oldItem.groupEntries[index] != newItem.groupEntries[index]) {
                return false
              }
            }
            return true
          }
          return false
        }

        override fun areItemsTheSame(
          oldItem: FeedGroup,
          newItem: FeedGroup
        ): Boolean {
          return oldItem.groupURI == newItem.groupURI
        }
      }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): CatalogFeedWithGroupsLaneViewHolder {
    val inflater =
      LayoutInflater.from(parent.context)
    val item =
      inflater.inflate(R.layout.feed_lane, parent, false)

    return CatalogFeedWithGroupsLaneViewHolder(
      parent = item,
      laneStyle = this.laneStyle,
      coverLoader = this.covers,
      screenSize = this.screenSize,
      onFeedSelected = this.onFeedSelected,
      onBookSelected = this.onBookSelected
    )
  }

  override fun onBindViewHolder(
    holder: CatalogFeedWithGroupsLaneViewHolder,
    position: Int
  ) {
    holder.bindTo(this.getItem(position))
  }

  override fun onViewRecycled(
    holder: CatalogFeedWithGroupsLaneViewHolder
  ) {
    holder.unbind()
  }

  override fun onDetachedFromRecyclerView(
    recyclerView: RecyclerView
  ) {
    // Nothing yet.
  }
}
