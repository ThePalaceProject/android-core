package org.nypl.simplified.ui.saml

import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import android.webkit.WebView
import org.slf4j.LoggerFactory

/**
 * Functions to configure web views for SAML.
 */

object SAMLWebViews {

  private val logger =
    LoggerFactory.getLogger(SAMLWebViews::class.java)

  /**
   * Attempt to ensure that the web view is starting from a completely clean slate.
   */

  fun clearWebViewState(
    webView: WebView
  ) {
    try {
      this.logger.debug("Clearing cookies.")
      val cookieManager = CookieManager.getInstance()
      cookieManager.removeAllCookies(null)
      cookieManager.flush()
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear cookies: ", e)
    }

    try {
      this.logger.debug("Clearing web storage.")
      val webStorage = WebStorage.getInstance()
      webStorage.deleteAllData()
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear web storage: ", e)
    }

    try {
      this.logger.debug("Clearing web cache.")
      webView.clearCache(true)
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear web cache: ", e)
    }

    try {
      this.logger.debug("Clearing web history.")
      webView.clearHistory()
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear web history: ", e)
    }

    try {
      this.logger.debug("Clearing web form data.")
      webView.clearFormData()
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear web form data: ", e)
    }

    try {
      this.logger.debug("Clearing web service workers.")
      val serviceWorkers = ServiceWorkerController.getInstance()
      serviceWorkers.serviceWorkerWebSettings.allowContentAccess = false
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear service workers: ", e)
    }
  }
}
