package org.nypl.simplified.ui.splash

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.documents.EULAType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.webview.WebViewUtilities
import org.slf4j.LoggerFactory

class EulaFragment : Fragment(R.layout.splash_eula) {

  private lateinit var agreeButton: Button
  private lateinit var disagreeButton: Button
  private lateinit var webview: WebView
  private lateinit var eula: EULAType

  private val logger = LoggerFactory.getLogger(EulaFragment::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val eula = Services.serviceDirectory()
      .requireService(DocumentStoreType::class.java)
      .eula

    this.eula = checkNotNull(eula)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.agreeButton = view.findViewById(R.id.splashEulaAgree)
    this.disagreeButton = view.findViewById(R.id.splashEulaDisagree)
    this.webview = view.findViewById(R.id.splashEulaWebView)

    with(this.webview.settings) {
      this.allowFileAccessFromFileURLs = true
      this.allowFileAccess = true
      this.allowContentAccess = true
      this.setSupportMultipleWindows(false)
      this.allowUniversalAccessFromFileURLs = false
      this.javaScriptEnabled = false
      WebViewUtilities.setForcedDark(this, this@EulaFragment.resources.configuration)
    }

    this.webview.webViewClient = MailtoWebViewClient(this.requireActivity())

    val url = this.eula.readableURL
    this.logger.debug("eula:     {}", this.eula)
    this.logger.debug("eula URL: {}", url)
    this.webview.loadUrl(url.toString())
  }

  override fun onStart() {
    super.onStart()
    this.agreeButton.setOnClickListener {
      this.eula.hasAgreed = true
      this.setResult()
    }
    this.disagreeButton.setOnClickListener {
      this.eula.hasAgreed = false
      this.setResult()
    }
  }

  private fun setResult() {
    this.setFragmentResult("", Bundle())
  }
}
