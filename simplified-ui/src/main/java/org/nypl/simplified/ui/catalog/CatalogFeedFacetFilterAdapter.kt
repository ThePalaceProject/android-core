package org.nypl.simplified.ui.catalog

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.catalog.CatalogFeedFacetFilterModel.FeedFacetModel
import java.util.Objects

class CatalogFeedFacetFilterAdapter() :
  ListAdapter<FeedFacetModel, CatalogFeedFacetFilterAdapter.CatalogFeedFacetFilterViewHolder>(
    diffCallback
  ) {

  class CatalogFeedFacetFilterViewHolder(
    private val parent: View,
  ) : RecyclerView.ViewHolder(parent) {
    private val title =
      this.parent.findViewById<TextView>(R.id.catalogFacetFilterTitle)
    private val clear =
      this.parent.findViewById<TextView>(R.id.catalogFacetFilterClear)
    private val icon =
      this.parent.findViewById<ImageView>(R.id.catalogFacetFilterIcon)
    private val radioGroup =
      this.parent.findViewById<RadioGroup>(R.id.catalogFacetFilterGroup)

    fun bindTo(
      item: FeedFacetModel
    ) {
      this.clear.paintFlags = this.clear.paintFlags or Paint.UNDERLINE_TEXT_FLAG
      this.title.text = item.title
      this.radioGroup.removeAllViews()

      for ((index, facet) in item.feedFacets.withIndex()) {
        val radioButton = RadioButton(this.parent.context)
        radioButton.text = facet.title
        radioButton.id = index
        radioButton.setOnClickListener {
          item.setSelected(index)
        }
        this.radioGroup.addView(radioButton)
        if (item.selectedIndex == index) {
          this.radioGroup.check(index)
        }
      }

      this.icon.setOnClickListener {
        item.expanded = !item.expanded
        this.reconfigureExpansion(item.expanded)
      }

      this.reconfigureExpansion(item.expanded)
    }

    private fun reconfigureExpansion(
      expanded: Boolean
    ) {
      if (expanded) {
        this.icon.setImageResource(R.drawable.chevron_down)
        this.radioGroup.visibility = View.VISIBLE
      } else {
        this.icon.setImageResource(R.drawable.chevron_right)
        this.radioGroup.visibility = View.GONE
      }
    }

    fun unbind() {
      this.icon.setOnClickListener(null)
      this.clear.setOnClickListener(null)
    }
  }

  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<FeedFacetModel>() {
        override fun areContentsTheSame(
          oldItem: FeedFacetModel,
          newItem: FeedFacetModel
        ): Boolean {
          return if (oldItem.title == newItem.title) {
            return Objects.equals(oldItem.feedFacets, newItem.feedFacets)
          } else {
            false
          }
        }

        override fun areItemsTheSame(
          oldItem: FeedFacetModel,
          newItem: FeedFacetModel
        ): Boolean {
          return oldItem.title == newItem.title
        }
      }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): CatalogFeedFacetFilterViewHolder {
    val inflater =
      LayoutInflater.from(parent.context)
    val item =
      inflater.inflate(R.layout.catalog_facet_filter_element, parent, false)

    return CatalogFeedFacetFilterViewHolder(
      parent = item
    )
  }

  override fun onBindViewHolder(
    holder: CatalogFeedFacetFilterViewHolder,
    position: Int
  ) {
    holder.bindTo(this.getItem(position))
  }

  override fun onViewRecycled(
    holder: CatalogFeedFacetFilterViewHolder
  ) {
    holder.unbind()
  }

  override fun onDetachedFromRecyclerView(
    recyclerView: RecyclerView
  ) {
    // Nothing yet.
  }
}
