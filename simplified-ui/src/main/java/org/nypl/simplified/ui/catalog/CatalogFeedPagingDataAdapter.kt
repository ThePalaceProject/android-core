package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.MoreExecutors
import org.librarysimplified.ui.R
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry

class CatalogFeedPagingDataAdapter(
  private val covers: BookCoverProviderType,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit
) : PagingDataAdapter<FeedEntry, CatalogFeedPagingDataAdapter.ViewHolder>(diffCallback) {

  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<FeedEntry>() {
        override fun areContentsTheSame(
          oldItem: FeedEntry,
          newItem: FeedEntry
        ): Boolean {
          return oldItem == newItem
        }

        override fun areItemsTheSame(
          oldItem: FeedEntry,
          newItem: FeedEntry
        ): Boolean {
          return oldItem.bookID == newItem.bookID
        }
      }
  }

  inner class ViewHolder(
    private val view: View
  ) : RecyclerView.ViewHolder(view) {

    private val idle =
      this.view.findViewById<ViewGroup>(R.id.bookCellIdle)
    private val corrupt =
      this.view.findViewById<ViewGroup>(R.id.bookCellCorrupt)
    private val error =
      this.view.findViewById<ViewGroup>(R.id.bookCellError)
    private val progress =
      this.view.findViewById<ViewGroup>(R.id.bookCellInProgress)

    private val idleCover =
      this.view.findViewById<ImageView>(R.id.bookCellIdleCover)
    private val idleCoverProgress =
      this.view.findViewById<ProgressBar>(R.id.bookCellIdleCoverProgress)
    private val idleTitle =
      this.idle.findViewById<TextView>(R.id.bookCellIdleTitle)
    private val idleMeta =
      this.idle.findViewById<TextView>(R.id.bookCellIdleMeta)
    private val idleAuthor =
      this.idle.findViewById<TextView>(R.id.bookCellIdleAuthor)
    private val idleButtons =
      this.idle.findViewById<ViewGroup>(R.id.bookCellIdleButtons)

    private val progressProgress =
      this.view.findViewById<ProgressBar>(R.id.bookCellInProgressBar)
    private val progressText =
      this.view.findViewById<TextView>(R.id.bookCellInProgressTitle)

    private val errorTitle =
      this.error.findViewById<TextView>(R.id.bookCellErrorTitle)
    private val errorDismiss =
      this.error.findViewById<Button>(R.id.bookCellErrorButtonDismiss)
    private val errorDetails =
      this.error.findViewById<Button>(R.id.bookCellErrorButtonDetails)
    private val errorRetry =
      this.error.findViewById<Button>(R.id.bookCellErrorButtonRetry)

    fun bind(
      item: FeedEntry
    ) {
      this.setVisible(this.progress, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.error, false)
      this.setVisible(this.corrupt, false)

      val targetHeight =
        this.view.resources.getDimensionPixelSize(R.dimen.catalogBookThumbnailHeight)
      val targetWidth = 0

      when (item) {
        is FeedEntry.FeedEntryCorrupt -> {
          this.view.setOnClickListener {
            // Nothing!
          }
        }

        is FeedEntry.FeedEntryOPDS -> {
          this.view.setOnClickListener {
            this@CatalogFeedPagingDataAdapter.onBookSelected(item)
          }

          this.setVisible(this.idleCover, false)
          this.setVisible(this.idleCoverProgress, true)

          this.idleTitle.text = item.feedEntry.title
          this.idleAuthor.text = item.feedEntry.authorsCommaSeparated

          val f =
            this@CatalogFeedPagingDataAdapter.covers.loadThumbnailInto(
              entry = item,
              imageView = this.idleCover,
              width = targetWidth,
              height = targetHeight
            )

          f.addListener({
            this.setVisible(this.idleCover, true)
            this.setVisible(this.idleCoverProgress, false)
          }, MoreExecutors.directExecutor())
        }
      }
    }

    private fun setVisible(
      target: View,
      visible: Boolean
    ) {
      /*
       * Setting the visibility of a view in Android has a cost, even if that view is already in the desired
       * visibility state. Therefore, we don't try to set the visibility of a view if the view is already
       * in the right state.
       */
      when (target.visibility) {
        VISIBLE -> {
          if (!visible) {
            target.visibility = INVISIBLE
          }
        }

        INVISIBLE -> {
          if (visible) {
            target.visibility = VISIBLE
          }
        }

        GONE -> {
          if (visible) {
            target.visibility = VISIBLE
          }
        }
      }
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): ViewHolder {
    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.book_cell, parent, false)

    return this.ViewHolder(view)
  }

  override fun onViewRecycled(
    holder: ViewHolder
  ) {
    // Nothing yet.
  }

  override fun onDetachedFromRecyclerView(
    recyclerView: RecyclerView
  ) {
    // Nothing yet.
  }

  override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int
  ) {
    val item = this.getItem(position)
    if (item != null) {
      (holder as? ViewHolder)?.bind(item)
    }
  }

  private fun isNearEnd(
    position: Int
  ): Boolean {
    if (this.itemCount == 0) {
      return false
    }
    val posF = position.toDouble()
    val endF = this.itemCount.toDouble()
    return (posF / endF) >= 0.8
  }
}
