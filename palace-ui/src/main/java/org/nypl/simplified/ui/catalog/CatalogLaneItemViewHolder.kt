package org.nypl.simplified.ui.catalog

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.images.ImageLoader2Type
import org.thepalaceproject.palace.images.ImageDimensions
import java.util.concurrent.CompletableFuture

/**
 * This adapter displays a list of feed items in a catalog lane.
 *
 * @see CatalogLaneItemViewHolder
 */
class CatalogLaneItemViewHolder(
  private val view: View,
  private val imageLoader: ImageLoader2Type,
  private val callbacks: CatalogViewCallbacksType,
) : RecyclerView.ViewHolder(view) {
  private var thumbnailLoading: CompletableFuture<Unit>? = null

  private val imageView = view.findViewById<ImageView>(R.id.coverImage)
  private val targetHeight =
    view.resources.getDimensionPixelSize(ImageDimensions.coverThumbnailHeight)

  fun bindTo(entry: FeedEntry.FeedEntryOPDS) {
    view.contentDescription =
      CatalogBookAccessibilityStrings.coverDescription(view.resources, entry)

    view.setOnClickListener {
      this.callbacks.onBookSelected(entry)
    }

    this.thumbnailLoading =
      this.imageLoader.loadThumbnailInto(entry, imageView, 0, targetHeight)
  }

  fun unbind() {
    this.thumbnailLoading =
      this.thumbnailLoading?.let { loading ->
        loading.cancel(true)
        null
      }

    view.contentDescription = null
    view.setOnClickListener(null)
  }
}
