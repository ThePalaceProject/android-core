package org.nypl.simplified.ui.catalog.saml20

import android.content.Context
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import io.reactivex.subjects.PublishSubject
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
import java.util.concurrent.atomic.AtomicReference

object CatalogSAML20Model {

  private var catalogWebViewClient: CatalogSAML20WebClient? = null

  private val logger =
    LoggerFactory.getLogger(CatalogSAML20Model::class.java)

  private val eventSubject =
    PublishSubject.create<CatalogSAML20WebClientEvent>()

  private val downloadInfo =
    AtomicReference<CatalogSAML20DownloadInfo>()

  sealed class WebViewRequest {
    data object None : WebViewRequest()

    data class Request(
      val url: String,
      val headers: Map<String, String>
    ) : WebViewRequest()
  }

  private val webViewRequestAttribute: AttributeType<WebViewRequest> =
    MainAttributes.attributes.withValue(WebViewRequest.None)
  private val webViewRequestAttributeUI: AttributeType<WebViewRequest> =
    MainAttributes.attributes.withValue(WebViewRequest.None)

  init {
    MainAttributes.wrapAttribute(
      source = this.webViewRequestAttribute,
      target = this.webViewRequestAttributeUI
    )
  }

  val request: AttributeReadableType<WebViewRequest> =
    this.webViewRequestAttributeUI

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

    this.catalogWebViewClient =
      CatalogSAML20WebClient(
        logger = this.logger,
        booksController = books,
        eventSubject = this.eventSubject,
        bookRegistry = bookRegistry,
        bookID = book,
        downloadURI = downloadURI,
        downloadInfo = this.downloadInfo,
        account = account,
        webViewDataDir = webViewDataDir
      )
  }

  fun onDownloadStarted(
    downloadURL: String?,
    mimeType: String?
  ) {
    val url = buildString {
      this.append(AccountSAML20.callbackURI)
      this.append("?")
      this.append("url=")
      this.append(URLEncoder.encode(downloadURL, "utf-8"))
      this.append("&")
      this.append("mimeType=")
      this.append(URLEncoder.encode(mimeType, "utf-8"))
    }

    this.webViewRequestAttribute.set(
      WebViewRequest.Request(
        url = url,
        headers = emptyMap()
      )
    )
  }
}
