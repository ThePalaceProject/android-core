package org.nypl.simplified.ui.accounts.saml20

import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.webview.WebViewUtilities
import org.slf4j.LoggerFactory
import java.io.File

class AccountSAML20WebClient(
  private val resources: Resources,
  private val description: AccountProviderAuthenticationDescription.SAML2_0,
  private val account: AccountID,
  private val webViewDataDir: File,
) : WebViewClient() {

  private val logger =
    LoggerFactory.getLogger(AccountSAML20WebClient::class.java)

  override fun onPageFinished(
    view: WebView?,
    url: String?
  ) {
    this.logger.debug("onPageFinished: {}", url)
  }

  override fun onPageStarted(
    view: WebView?,
    url: String?,
    favicon: Bitmap?
  ) {
    this.logger.debug("onPageStarted: {}", url)
  }

  @Deprecated("Deprecated in Java")
  override fun shouldOverrideUrlLoading(
    view: WebView,
    url: String
  ): Boolean {
    return this.handleUrl(view, url)
  }

  override fun shouldOverrideUrlLoading(
    view: WebView,
    request: WebResourceRequest
  ): Boolean {
    return this.handleUrl(view, request.url.toString())
  }

  private fun handleUrl(
    view: WebView,
    url: String
  ): Boolean {
    this.logger.debug("handleUrl: {}", url)

    if (url.startsWith(AccountSAML20.callbackURI)) {
      this.logger.debug("handleUrl: Callback URI detected")

      val parsed = Uri.parse(url)
      val accessToken = parsed.getQueryParameter("access_token")
      if (accessToken == null) {
        val message = this.resources.getString(R.string.accountSAML20NoAccessToken, url)
        this.logger.error("{}", message)
        AccountSAML20Model.setState(
          AccountSAML20State.Failed(
            accountID = this.account,
            description = this.description,
            message = message,
            webView = view
          )
        )
        return true
      }

      val patronInfo = parsed.getQueryParameter("patron_info")
      if (patronInfo == null) {
        val message = this.resources.getString(R.string.accountSAML20NoPatronInfo, url)
        this.logger.error("{}", message)
        AccountSAML20Model.setState(
          AccountSAML20State.Failed(
            accountID = this.account,
            description = this.description,
            message = message,
            webView = view
          )
        )
        return true
      }

      val cookies = WebViewUtilities.dumpCookiesAsAccountCookies(
        CookieManager.getInstance(),
        this.webViewDataDir
      )

      this.logger.debug("Obtained access token")
      AccountSAML20Model.setState(
        AccountSAML20State.TokenObtained(
          accountID = this.account,
          token = accessToken,
          patronInfo = patronInfo,
          cookies = cookies,
          webView = view
        )
      )
      return true
    }

    return false
  }
}
