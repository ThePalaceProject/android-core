package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import org.librarysimplified.ui.R

class CatalogFeedViewError(
  override val root: ViewGroup,
  private val onShowErrorDetails: () -> Unit,
  private val onRetry: () -> Unit,
) : CatalogFeedView() {

  private val errorDetails: Button =
    this.root.findViewById(R.id.feedErrorDetails)
  private val errorRetry: Button =
    this.root.findViewById(R.id.feedErrorRetry)

  init {
    this.errorDetails.setOnClickListener { this.onShowErrorDetails.invoke() }
    this.errorRetry.setOnClickListener { this.onRetry.invoke() }
  }

  companion object {
    fun create(
      layoutInflater: LayoutInflater,
      container: ViewGroup,
      onShowErrorDetails: () -> Unit,
      onRetry: () -> Unit,
    ): CatalogFeedViewError {
      return CatalogFeedViewError(
        root = layoutInflater.inflate(
          R.layout.catalog_feed_error,
          container,
          true
        ) as ViewGroup,
        onShowErrorDetails = onShowErrorDetails,
        onRetry = onRetry
      )
    }
  }

  override fun clear() {
    this.root.isEnabled = false
  }
}
