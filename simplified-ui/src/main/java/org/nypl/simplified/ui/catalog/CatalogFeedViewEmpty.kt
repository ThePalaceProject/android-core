package org.nypl.simplified.ui.catalog

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import org.librarysimplified.ui.R

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

  override fun startFocus() {
    // Nothing required.
  }

  override fun clear() {
    this.root.isEnabled = false
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    // Nothing to do
  }
}
