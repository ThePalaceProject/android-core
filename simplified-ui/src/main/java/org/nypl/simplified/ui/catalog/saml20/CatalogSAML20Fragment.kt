package org.nypl.simplified.ui.catalog.saml20

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R

/**
 * A fragment that performs the SAML 2.0 borrowing login workflow.
 */

class CatalogSAML20Fragment : Fragment(R.layout.book_saml20) {

  private lateinit var progress: ProgressBar
  private lateinit var webView: WebView

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.progress =
      view.findViewById(R.id.saml20progressBar)
    this.webView =
      view.findViewById(R.id.saml20WebView)

    TODO()
  }
}
