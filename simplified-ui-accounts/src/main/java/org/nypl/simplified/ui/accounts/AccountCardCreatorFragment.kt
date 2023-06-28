package org.nypl.simplified.ui.accounts

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.ui.neutrality.NeutralToolbar
import org.nypl.simplified.webview.WebViewUtilities

/**
 * A fragment that shows a WebView
 */

class AccountCardCreatorFragment : Fragment(R.layout.fragment_account_card_creator) {

  private val listener: FragmentListenerType<AccountDetailEvent> by fragmentListeners()

  private lateinit var toolbar: NeutralToolbar
  private lateinit var webView: WebView

  companion object {

    private const val BUNDLE_EXTRA_PARAMETERS =
      "org.nypl.simplified.ui.accounts.AccountCardCreatorFragment.parameters"

    /**
     * Create a new account fragment for the given parameters.
     */

    fun create(parameters: AccountCardCreatorParameters): AccountCardCreatorFragment {
      return AccountCardCreatorFragment().apply {
        arguments = bundleOf(BUNDLE_EXTRA_PARAMETERS to parameters)
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.findViewById(R.id.toolbar)
    this.webView =
      view.findViewById(R.id.web_view)

    this.configureToolbar()
    this.configureWebView()
  }

  override fun onDestroyView() {
    if (::webView.isInitialized) {
      webView.destroy()
    }
    super.onDestroyView()
  }

  private fun configureWebView() {
    val parameters = requireArguments().getSerializable(BUNDLE_EXTRA_PARAMETERS) as?
      AccountCardCreatorParameters ?: throw Exception("Invalid parameters passed")

    val url = Uri.parse(parameters.url)
      .buildUpon()
      .appendQueryParameter("lat", parameters.lat.toString())
      .appendQueryParameter("long", parameters.long.toString())
      .toString()

    webView.apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      webChromeClient = WebChromeClient()
      webViewClient = WebViewClient()
      WebViewUtilities.setForcedDark(settings, resources.configuration)

      loadUrl(url)
    }
  }

  private fun configureToolbar() {
    val actionBar = this.supportActionBar ?: return
    actionBar.show()
    actionBar.setDisplayHomeAsUpEnabled(true)
    actionBar.setHomeActionContentDescription(null)
    this.toolbar.setLogoOnClickListener {
      this.listener.post(AccountDetailEvent.GoUpwards)
    }
  }
}
