package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.webview.WebViewUtilities

class SettingsDocumentViewerFragment : Fragment() {

  private lateinit var documentWebView: WebView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view =
      inflater.inflate(R.layout.settings_document_viewer, container, false)

    this.documentWebView = view.findViewById<WebView>(R.id.documentViewerWebView)
    return view
  }

  override fun onStart() {
    super.onStart()

    val target = SettingsDocumentViewerModel.target
    if (target != null) {
      this.documentWebView.webViewClient = WebViewClient()
      this.documentWebView.webChromeClient = WebChromeClient()
      this.documentWebView.settings.allowFileAccess = true
      WebViewUtilities.setForcedDark(this.documentWebView.settings, resources.configuration)
      this.documentWebView.loadUrl(target.url)
    } else {
      TODO()
    }
  }
}
