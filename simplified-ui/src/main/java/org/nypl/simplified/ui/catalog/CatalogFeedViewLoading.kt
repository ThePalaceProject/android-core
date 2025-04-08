package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import org.librarysimplified.ui.R

class CatalogFeedViewLoading(
  override val root: ViewGroup
) : CatalogFeedView() {

  companion object {
    fun create(
      layoutInflater: LayoutInflater,
      container: ViewGroup
    ): CatalogFeedViewLoading {
      return CatalogFeedViewLoading(
        layoutInflater.inflate(
          R.layout.catalog_feed_loading,
          container,
          true
        ) as ViewGroup
      )
    }
  }

  override fun clear() {
    this.root.isEnabled = false
  }
}
