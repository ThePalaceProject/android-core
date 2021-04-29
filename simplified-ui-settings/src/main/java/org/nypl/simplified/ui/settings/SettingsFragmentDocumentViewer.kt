package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.ui.settings.databinding.SettingsDocumentViewerBinding

class SettingsFragmentDocumentViewer : Fragment() {

  private lateinit var binding: SettingsDocumentViewerBinding

  private val title by lazy { arguments?.getString(TITLE_ID) }
  private val url by lazy { arguments?.getString(URL_ID) }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    binding = SettingsDocumentViewerBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    supportActionBar?.apply {
      title = this@SettingsFragmentDocumentViewer.title
      subtitle = null
    }

    if (!url.isNullOrBlank()) {
      binding.documentViewerWebView.webViewClient = WebViewClient()
      binding.documentViewerWebView.webChromeClient = WebChromeClient()
      binding.documentViewerWebView.loadUrl(url)
    }
  }

  companion object {
    private const val TITLE_ID = "org.nypl.simplified.ui.settings.SettingsFragmentDocumentViewer.title"
    private const val URL_ID = "org.nypl.simplified.ui.settings.SettingsFragmentDocumentViewer.url"

    fun create(title: String, url: String) = SettingsFragmentDocumentViewer().apply {
      arguments = bundleOf(TITLE_ID to title, URL_ID to url)
    }
  }
}
