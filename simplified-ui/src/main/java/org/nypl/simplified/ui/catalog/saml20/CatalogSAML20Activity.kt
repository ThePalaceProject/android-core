package org.nypl.simplified.ui.catalog.saml20

import android.webkit.WebView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.ui.R
import org.nypl.simplified.webview.WebViewUtilities

/**
 * An activity that performs the SAML 2.0 borrowing login workflow.
 */

class CatalogSAML20Activity : AppCompatActivity(R.layout.book_saml20) {

  private var subscriptions = CloseableCollection.create()
  private lateinit var progress: ProgressBar
  private lateinit var webView: WebView

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }

  override fun onStart() {
    super.onStart()

    this.progress =
      this.findViewById(R.id.saml20progressBar)
    this.webView =
      this.findViewById(R.id.saml20WebView)

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
        CatalogSAML20Model.borrowState.subscribe { _, state ->
          when (state) {
            is CatalogSAML20BorrowState.MakeRequest -> {
              this.webView.loadUrl(state.url, state.headers)
            }
            CatalogSAML20BorrowState.Finished -> {
              this.finish()
            }
            is CatalogSAML20BorrowState.Idle -> {
              // Nothing to do.
            }
            is CatalogSAML20BorrowState.WebViewReady -> {
              // Nothing to do.
            }
          }
        }
      )
    }
  }
}
