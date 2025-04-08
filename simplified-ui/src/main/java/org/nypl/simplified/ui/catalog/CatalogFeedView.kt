package org.nypl.simplified.ui.catalog

import android.view.ViewGroup

sealed class CatalogFeedView {
  abstract fun clear()
  abstract val root: ViewGroup
}
