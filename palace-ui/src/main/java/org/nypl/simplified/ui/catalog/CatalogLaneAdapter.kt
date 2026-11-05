package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.librarysimplified.ui.R
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.ui.images.ImageLoader2Type

/**
 * This adapter displays a list of feed items in a catalog lane.
 *
 * @see CatalogLaneItemViewHolder
 */
class CatalogLaneAdapter(
  private val imageLoader: ImageLoader2Type,
  private val callbacks: CatalogViewCallbacksType,
) : ListAdapter<FeedEntryBook, CatalogLaneItemViewHolder>(diffCallback) {
  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<FeedEntryBook>() {
        override fun areContentsTheSame(
          oldItem: FeedEntryBook,
          newItem: FeedEntryBook
        ): Boolean = oldItem == newItem

        override fun areItemsTheSame(
          oldItem: FeedEntryBook,
          newItem: FeedEntryBook
        ): Boolean = oldItem.id == newItem.id
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

    return CatalogLaneItemViewHolder(view, imageLoader, this.callbacks)
  }

  override fun onBindViewHolder(
    holder: CatalogLaneItemViewHolder,
    position: Int
  ) {
    holder.bindTo(this.getItem(position))
  }

  override fun onViewRecycled(holder: CatalogLaneItemViewHolder) {
    super.onViewRecycled(holder)
    holder.unbind()
  }
}
