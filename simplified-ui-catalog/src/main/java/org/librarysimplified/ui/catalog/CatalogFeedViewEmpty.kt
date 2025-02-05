package org.librarysimplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup

class CatalogFeedViewEmpty(
  override val root: ViewGroup
) : CatalogFeedView() {

  companion object {
    fun create(
      layoutInflater: LayoutInflater,
      container: ViewGroup
    ): CatalogFeedViewEmpty {
      return CatalogFeedViewEmpty(
        layoutInflater.inflate(
          R.layout.catalog_feed_empty,
          container,
          true
        ) as ViewGroup
      )
    }
  }
}
