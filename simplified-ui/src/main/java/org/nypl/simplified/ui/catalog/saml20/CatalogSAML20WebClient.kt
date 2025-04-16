package org.nypl.simplified.ui.catalog.saml20

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20
import org.nypl.simplified.webview.WebViewUtilities
import org.slf4j.Logger
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

class CatalogSAML20WebClient(
  private val logger: Logger,
  private val booksController: BooksControllerType,
  private val eventSubject: PublishSubject<CatalogSAML20WebClientEvent>,
  private val bookRegistry: BookRegistryType,
  private val bookID: BookID,
  private val downloadURI: URI,
  private val downloadInfo: AtomicReference<CatalogSAML20DownloadInfo>,
  private val account: AccountType,
  private val webViewDataDir: File
) : WebViewClient() {

  var isReady = false

  init {
    /*
     * Remove any existing cookies from the web view, and add the cookies associated with this
     * account.
     */

    val cookieManager = CookieManager.getInstance()

    cookieManager.removeAllCookies {
      val credentials = account.loginState.credentials

      if (credentials is AccountAuthenticationCredentials.SAML2_0) {
        credentials.cookies.forEach { accountCookie ->
          cookieManager.setCookie(accountCookie.url, accountCookie.value)
        }
      }

      isReady = true

      this.eventSubject.onNext(
        CatalogSAML20WebClientEvent.WebViewClientReady
      )
    }
  }

  override fun onLoadResource(
    view: WebView,
    url: String
  ) {
    if (url.startsWith(AccountSAML20.callbackURI)) {
      val parsed = Uri.parse(url)

      val downloadURL = parsed.getQueryParameter(("url"))!!
      val mimeType = parsed.getQueryParameter("mimeType")!!

      this.logger.debug("obtained download info")
      this.downloadInfo.set(
        CatalogSAML20DownloadInfo(
          url = downloadURL,
          mimeType = mimeType
        )
      )

      val cookies = WebViewUtilities.dumpCookiesAsAccountCookies(
        CookieManager.getInstance(),
        this.webViewDataDir
      )

      val loginState = account.loginState

      if (loginState is AccountLoginState.AccountLoggedIn) {
        val credentials = loginState.credentials

        if (credentials is AccountAuthenticationCredentials.SAML2_0) {
          account.setLoginState(
            loginState.copy(
              credentials = credentials.copy(
                cookies = cookies
              )
            )
          )
        }
      }

      val bookWithStatus = this.bookRegistry.bookOrNull(this.bookID)

      if (bookWithStatus != null) {
        val book = bookWithStatus.book

        this.booksController.bookBorrow(
          accountID = book.account,
          bookID = book.id,
          entry = book.entry,
          samlDownloadContext = SAMLDownloadContext(
            isSAMLAuthComplete = true,
            downloadURI = this.downloadURI,
            authCompleteDownloadURI = URI(downloadURL)
          )
        )
      }

      this.eventSubject.onNext(
        CatalogSAML20WebClientEvent.Succeeded
      )
    }
  }
}
