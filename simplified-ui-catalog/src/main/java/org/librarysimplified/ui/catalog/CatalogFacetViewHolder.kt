package org.librarysimplified.ui.catalog

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.nypl.simplified.feeds.api.FeedFacet

class CatalogFacetViewHolder(
  private val parent: View,
  private val onFacetSelected: (FeedFacet) -> Unit,
) : ViewHolder(parent) {

  private val chips: ChipGroup =
    this.parent.findViewById(R.id.facetSelectionValues)
  private val title: TextView =
    this.parent.findViewById(R.id.facetSelectionTitle)

  fun bindTo(
    entry: Pair<String, List<FeedFacet>>
  ) {
    this.title.text = entry.first
    this.chips.removeAllViews()

    entry.second
      .sortedBy { f -> f.title }
      .forEach { f ->
        val chip = Chip(this.parent.context)
        chip.text = f.title
        chip.isCheckable = false
        chip.isCloseIconVisible = false
        chip.setOnClickListener { this.onFacetSelected.invoke(f) }
        this.chips.addView(chip)
      }
  }
}
