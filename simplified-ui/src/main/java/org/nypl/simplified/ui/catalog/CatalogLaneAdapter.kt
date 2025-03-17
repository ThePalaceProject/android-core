package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.librarysimplified.ui.R
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry

/**
 * This adapter displays a list of feed items in a catalog lane.
 *
 * @see CatalogLaneItemViewHolder
 */
class CatalogLaneAdapter(
  private val coverLoader: BookCoverProviderType,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit
) : ListAdapter<FeedEntry.FeedEntryOPDS, CatalogLaneItemViewHolder>(diffCallback) {

  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<FeedEntry.FeedEntryOPDS>() {
        override fun areContentsTheSame(
          oldItem: FeedEntry.FeedEntryOPDS,
          newItem: FeedEntry.FeedEntryOPDS
        ): Boolean {
          return oldItem == newItem
        }

        override fun areItemsTheSame(
          oldItem: FeedEntry.FeedEntryOPDS,
          newItem: FeedEntry.FeedEntryOPDS
        ): Boolean {
          return oldItem.feedEntry.id == newItem.feedEntry.id
        }
      }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): CatalogLaneItemViewHolder {
    val inflater =
      LayoutInflater.from(parent.context)
    val view =
      inflater.inflate(R.layout.feed_lane_item, parent, false)

    return CatalogLaneItemViewHolder(view, coverLoader, onBookSelected)
  }

  override fun onBindViewHolder(
    holder: CatalogLaneItemViewHolder,
    position: Int
  ) {
    holder.bindTo(this.getItem(position))
  }

  override fun onViewRecycled(
    holder: CatalogLaneItemViewHolder
  ) {
    super.onViewRecycled(holder)
    holder.unbind()
  }
}
