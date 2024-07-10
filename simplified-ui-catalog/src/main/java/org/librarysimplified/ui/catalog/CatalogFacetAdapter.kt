package org.librarysimplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.nypl.simplified.feeds.api.FeedFacet

class CatalogFacetAdapter(
  private val items: List<Pair<String, List<FeedFacet>>>,
  private val onSelectFacet: (FeedFacet) -> Unit,
) : RecyclerView.Adapter<CatalogFacetViewHolder>() {

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): CatalogFacetViewHolder {
    return CatalogFacetViewHolder(
      parent = LayoutInflater.from(parent.context)
        .inflate(R.layout.facet_selection_item, parent, false),
      onFacetSelected = this.onSelectFacet
    )
  }

  override fun onBindViewHolder(
    holder: CatalogFacetViewHolder,
    position: Int
  ) {
    holder.bindTo(this.items[position])
  }

  override fun getItemCount(): Int {
    return this.items.size
  }
}
