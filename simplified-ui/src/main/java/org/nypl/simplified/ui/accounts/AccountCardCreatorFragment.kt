package org.nypl.simplified.ui.accounts

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.nypl.simplified.webview.WebViewUtilities

/**
 * A fragment that shows a WebView
 */

class AccountCardCreatorFragment : Fragment(R.layout.fragment_account_card_creator) {

  private lateinit var webView: WebView

  companion object :
    ScreenDefinitionFactoryType<AccountCardCreatorParameters, AccountCardCreatorFragment> {
    private class ScreenSettingsCardCreator(
      private val parameters: AccountCardCreatorParameters
    ) : ScreenDefinitionType<AccountCardCreatorParameters, AccountCardCreatorFragment> {
      override fun setup() {
        AccountCardCreatorModel.parameters = this.parameters
      }

      override fun parameters(): AccountCardCreatorParameters {
        return this.parameters
      }

      override fun fragment(): AccountCardCreatorFragment {
        return AccountCardCreatorFragment()
      }
    }

    override fun createScreenDefinition(
      p: AccountCardCreatorParameters
    ): ScreenDefinitionType<AccountCardCreatorParameters, AccountCardCreatorFragment> {
      return ScreenSettingsCardCreator(p)
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.webView = view.findViewById(R.id.web_view)
    this.configureWebView()
  }

  private fun configureWebView() {
    val parameters =
      AccountCardCreatorModel.parameters

    val url =
      Uri.parse(parameters.url)
        .buildUpon()
        .appendQueryParameter("lat", parameters.lat.toString())
        .appendQueryParameter("long", parameters.long.toString())
        .toString()

    this.webView.apply {
      this.settings.javaScriptEnabled = true
      this.settings.domStorageEnabled = true
      this.webChromeClient = WebChromeClient()
      this.webViewClient = WebViewClient()
      WebViewUtilities.setForcedDark(this.settings, this.resources.configuration)
      this.loadUrl(url)
    }
  }
}
