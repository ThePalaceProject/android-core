package org.librarysimplified.ui.catalog

import android.view.ViewGroup

sealed class CatalogFeedView {
  abstract val root: ViewGroup
}
