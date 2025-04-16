package org.nypl.simplified.ui.catalog.saml20

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.ui.R
import org.nypl.simplified.webview.WebViewUtilities

/**
 * A fragment that performs the SAML 2.0 borrowing login workflow.
 */

class CatalogSAML20Fragment : Fragment(R.layout.book_saml20) {

  private var subscriptions = CloseableCollection.create()
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
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()

    val client = CatalogSAML20Model.client
    if (client != null) {
      this.webView.webChromeClient = CatalogSAML20ChromeClient(this.progress)
      this.webView.webViewClient = client
      this.webView.settings.javaScriptEnabled = true
      WebViewUtilities.setForcedDark(this.webView.settings, resources.configuration)

      this.webView.setDownloadListener { url, _, _, mime, _ ->
        CatalogSAML20Model.onDownloadStarted(
          downloadURL = url,
          mimeType = mime
        )
      }

      this.subscriptions.add(
        CatalogSAML20Model.request.subscribe { _, request ->
          when (request) {
            CatalogSAML20Model.WebViewRequest.None -> {
              // Nothing to do.
            }

            is CatalogSAML20Model.WebViewRequest.Request -> {
              this.webView.loadUrl(request.url, request.headers)
            }
          }
        }
      )
    }
  }
}
