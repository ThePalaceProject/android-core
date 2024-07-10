package org.librarysimplified.ui.catalog

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CatalogFacetDialog : BottomSheetDialogFragment(R.layout.facet_selection) {

  private lateinit var facetList: RecyclerView
  private lateinit var facetAdapter: CatalogFacetAdapter

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.facetList =
      view.findViewById(R.id.facetSelectionList)
    this.facetAdapter =
      CatalogFacetAdapter(
        items = CatalogFacetModel.facetAsList,
        onSelectFacet = { f ->
          CatalogFacetModel.onSelectFacet.invoke(f)
          this.dialog?.cancel()
        }
      )

    this.facetList.layoutManager = LinearLayoutManager(this.context)
    this.facetList.adapter = this.facetAdapter
  }
}
