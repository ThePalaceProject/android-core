package org.nypl.simplified.ui.catalog.saml20

sealed class CatalogSAML20BorrowState {

  data object Idle : CatalogSAML20BorrowState()

  data class WebViewReady(
    val startURL: String
  ) : CatalogSAML20BorrowState()

  data class MakeRequest(
    val url: String,
    val headers: Map<String, String>
  ) : CatalogSAML20BorrowState()

  data object Finished : CatalogSAML20BorrowState()
}
