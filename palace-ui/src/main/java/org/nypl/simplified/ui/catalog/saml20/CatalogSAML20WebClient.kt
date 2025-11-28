package org.nypl.simplified.ui.catalog.saml20

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.io7m.jattribute.core.AttributeType
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

class CatalogSAML20WebClient(
  private val logger: Logger,
  private val booksController: BooksControllerType,
  private val borrowStateAttribute: AttributeType<CatalogSAML20BorrowState>,
  private val bookRegistry: BookRegistryType,
  private val bookID: BookID,
  private val downloadURI: URI,
  private val account: AccountType,
  private val webViewDataDir: File,
  private val startURL: String,
) : WebViewClient() {

  private var isReady = false

  init {
    /*
     * Remove any existing cookies from the web view, and add the cookies associated with this
     * account.
     */

    val cookieManager =
      CookieManager.getInstance()

    cookieManager.removeAllCookies {
      val credentials = this.account.loginState.credentials

      if (credentials is AccountAuthenticationCredentials.SAML2_0) {
        credentials.cookies.forEach { accountCookie ->
          cookieManager.setCookie(accountCookie.url, accountCookie.value)
        }
      }

      this.isReady = true
      this.borrowStateAttribute.set(CatalogSAML20BorrowState.WebViewReady(startURL))
      this.borrowStateAttribute.set(CatalogSAML20BorrowState.MakeRequest(startURL, mapOf()))
    }
  }

  override fun onLoadResource(
    view: WebView,
    url: String
  ) {
    if (url.startsWith(AccountSAML20.callbackURI)) {
      try {
        this.logger.debug("Callback URI received ({}).", url)

        val parsed =
          Uri.parse(url)
        val downloadURL =
          parsed.getQueryParameter(("url"))!!

        val cookies =
          WebViewUtilities.dumpCookiesAsAccountCookies(
            CookieManager.getInstance(),
            this.webViewDataDir
          )

        val loginState = this.account.loginState
        if (loginState is AccountLoginState.AccountLoggedIn) {
          val credentials = loginState.credentials

          if (credentials is AccountAuthenticationCredentials.SAML2_0) {
            this.account.setLoginState(
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
      } finally {
        this.borrowStateAttribute.set(CatalogSAML20BorrowState.Finished)
        this.borrowStateAttribute.set(CatalogSAML20BorrowState.Idle)
      }
    }
  }
}
