package org.nypl.simplified.ui.catalog.saml20

import android.content.Context
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainAttributes
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder

object CatalogSAML20Model {

  private var catalogWebViewClient: CatalogSAML20WebClient? = null

  private val logger =
    LoggerFactory.getLogger(CatalogSAML20Model::class.java)

  private val borrowStateAttribute: AttributeType<CatalogSAML20BorrowState> =
    MainAttributes.attributes.withValue(CatalogSAML20BorrowState.Idle)
  private val webViewCommandAttributeUI: AttributeType<CatalogSAML20BorrowState> =
    MainAttributes.attributes.withValue(CatalogSAML20BorrowState.Idle)

  init {
    MainAttributes.wrapAttribute(
      source = this.borrowStateAttribute,
      target = this.webViewCommandAttributeUI
    )
  }

  val borrowState: AttributeReadableType<CatalogSAML20BorrowState> =
    this.webViewCommandAttributeUI

  val client
    get() = this.catalogWebViewClient

  fun start(
    account: AccountType,
    book: BookID,
    downloadURI: URI
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)
    val bookRegistry =
      services.requireService(BookRegistryType::class.java)
    val webViewDataDir =
      MainApplication.application.getDir("webview", Context.MODE_PRIVATE)

    this.borrowStateAttribute.set(CatalogSAML20BorrowState.Idle)

    this.catalogWebViewClient =
      CatalogSAML20WebClient(
        logger = this.logger,
        booksController = books,
        borrowStateAttribute = this.borrowStateAttribute,
        bookRegistry = bookRegistry,
        bookID = book,
        downloadURI = downloadURI,
        account = account,
        webViewDataDir = webViewDataDir,
        startURL = downloadURI.toString()
      )
  }

  fun onDownloadStarted(
    downloadURL: String?,
    mimeType: String?
  ) {
    this.logger.debug("WebView reported download started.")

    val url = buildString {
      this.append(AccountSAML20.callbackURI)
      this.append("?")
      this.append("url=")
      this.append(URLEncoder.encode(downloadURL, "utf-8"))
      this.append("&")
      this.append("mimeType=")
      this.append(URLEncoder.encode(mimeType, "utf-8"))
    }

    this.logger.debug("Redirecting WebView to {}", url)

    this.borrowStateAttribute.set(
      CatalogSAML20BorrowState.MakeRequest(
        url = url,
        headers = emptyMap()
      )
    )
  }
}
