package org.nypl.simplified.viewer.pdf.pdfjs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.common.util.concurrent.MoreExecutors
import org.joda.time.DateTime
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.ServerSocket
import java.util.ServiceLoader
import java.util.concurrent.Executors

class PdfReaderActivity : AppCompatActivity() {

  companion object {
    private const val PARAMS_ID = "org.nypl.simplified.viewer.pdf.pdfjs.PdfReaderActivity.params"

    /**
     * Factory method to start a [PdfReaderActivity]
     */
    fun startActivity(
      from: Activity,
      parameters: PdfReaderParameters
    ) {
      val bundle = Bundle().apply {
        putSerializable(PARAMS_ID, parameters)
      }

      val intent = Intent(from, PdfReaderActivity::class.java).apply {
        putExtras(bundle)
      }

      from.startActivity(intent)
    }
  }

  private val log: Logger = LoggerFactory.getLogger(PdfReaderActivity::class.java)

  private val services =
    Services.serviceDirectory()
  private val bookmarkService =
    services.requireService(BookmarkServiceType::class.java)
  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private lateinit var handle: BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
  private lateinit var uiThread: UIThreadServiceType
  private lateinit var pdfReaderContainer: FrameLayout
  private lateinit var accountId: AccountID
  private lateinit var bookID: BookID
  private lateinit var feedEntry: FeedEntry.FeedEntryOPDS
  private lateinit var loadingBar: ProgressBar
  private lateinit var webView: WebView

  private var pdfServer: PdfServer? = null
  private var isSidebarOpen = false
  private var documentPageIndex: Int = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val params = intent?.getSerializableExtra(PARAMS_ID) as PdfReaderParameters

    setContentView(R.layout.pdfjs_reader)
    createToolbar(params.documentTitle)

    this.loadingBar = findViewById(R.id.pdf_loading_progress)

    this.accountId = params.accountId
    this.feedEntry = params.entry
    this.bookID = params.id

    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)

    val backgroundThread = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))

    backgroundThread.execute {
      restoreSavedPosition()

      this.uiThread.runOnUIThread {
        this.loadingBar.visibility = View.GONE

        if (savedInstanceState == null) {
          createWebView()
          createPdfServer(params.drmInfo, params.pdfFile)

          this.pdfServer?.let {
            it.start()

            this.webView.loadUrl(
              "http://localhost:${it.port}/assets/pdf-viewer/viewer.html?file=%2Fbook.pdf#page=${this.documentPageIndex}"
            )

            this.webView.webViewClient = object : WebViewClient() {
              override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript("isSideBarOpen()") { result ->
                  if (result == "true") {
                    toggleSidebar()
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private fun restoreSavedPosition() {

    val bookmarks =
      PdfReaderBookmarks.loadBookmarks(
        bookmarkService = this.bookmarkService,
        accountID = this.accountId,
        bookID = this.bookID
      )

    try {
      val books =
        services.requireService(ProfilesControllerType::class.java)
          .profileCurrent()
          .account(this.accountId)
          .bookDatabase
      val entry = books.entry(this.bookID)
      this.handle = entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF::class.java)!!

      val bookMarkLastReadPosition = bookmarks
        .filterIsInstance<Bookmark.PDFBookmark>()
        .find { bookmark ->
          bookmark.kind == BookmarkKind.BookmarkLastReadLocation
        }

      this.documentPageIndex =
        bookMarkLastReadPosition?.pageNumber ?: this.handle.format.lastReadLocation!!.pageNumber
    } catch (e: Exception) {
      log.error("Could not get lastReadLocation, defaulting to the 1st page", e)
    }
  }

  private fun createToolbar(title: String) {
    val toolbar = this.findViewById(R.id.pdf_toolbar) as Toolbar

    this.setSupportActionBar(toolbar)
    this.supportActionBar?.setHomeActionContentDescription(R.string.content_description_back)

    this.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    this.supportActionBar?.setDisplayShowHomeEnabled(true)
    this.supportActionBar?.title = title
  }

  private fun createWebView() {
    WebView.setWebContentsDebuggingEnabled(true)

    this.webView = WebView(this).apply {
      settings.javaScriptEnabled = true
    }

    this.webView.addJavascriptInterface(
      object {
        @JavascriptInterface
        fun onPageChanged(pageIndex: Int) {
          this@PdfReaderActivity.onReaderPageChanged(pageIndex)
        }
      },
      "PDFListener"
    )

    this.pdfReaderContainer = findViewById(R.id.pdf_reader_container)
    this.pdfReaderContainer.addView(this.webView)
  }

  private fun createPdfServer(drmInfo: BookDRMInformation, pdfFile: File) {
    // Create an immediately-closed socket to get a free port number.
    val ephemeralSocket = ServerSocket(0).apply { close() }

    try {
      this.pdfServer = PdfServer(
        port = ephemeralSocket.localPort,
        context = this,
        contentProtectionProviders =
          ServiceLoader.load(ContentProtectionProvider::class.java).toList(),
        drmInfo = drmInfo,
        pdfFile = pdfFile
      )
    } catch (exception: Exception) {
      showErrorWithRunnable(
        context = this,
        title = exception.message ?: "",
        failure = exception,
        execute = this::finish
      )
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.pdf_reader_menu, menu)

    menu?.findItem(R.id.readerMenuTOC)?.setOnMenuItemClickListener {
      this.onReaderMenuTOCSelected()
    }

    menu?.findItem(R.id.readerMenuSettings)?.setOnMenuItemClickListener {
      this.onReaderMenuSettingsSelected()
    }

    return true
  }

  private fun onReaderMenuTOCSelected(): Boolean {
    this.toggleSidebar()

    return true
  }

  private fun toggleSidebar() {
    if (::webView.isInitialized) {
      this.webView.evaluateJavascript("toggleSidebar()") { result ->
        this.isSidebarOpen = (result == "true")
      }
    }
  }

  private fun onReaderMenuSettingsSelected(): Boolean {
    if (::webView.isInitialized) {
      this.webView.evaluateJavascript("toggleSecondaryToolbar()", null)
    }

    return true
  }

  override fun onDestroy() {
    super.onDestroy()

    this.pdfServer?.stop()
    this.pdfReaderContainer.removeAllViews()
    this.webView.destroy()
  }

  override fun onBackPressed() {
    if (this.isSidebarOpen) {
      this.toggleSidebar()
    } else {
      super.onBackPressed()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    }

    return super.onOptionsItemSelected(item)
  }

  private fun onReaderPageChanged(pageIndex: Int) {
    log.debug("onReaderPageChanged {}", pageIndex)

    this.documentPageIndex = pageIndex

    val bookmark = Bookmark.PDFBookmark.create(
      opdsId = this.feedEntry.feedEntry.id,
      time = DateTime.now(),
      kind = BookmarkKind.BookmarkLastReadLocation,
      pageNumber = pageIndex,
      deviceID = PdfReaderDevices.deviceId(this.profilesController, this.bookID),
      uri = null
    )

    this.bookmarkService.bookmarkCreateLocal(
      accountID = this.accountId,
      bookmark = bookmark
    )

    this.bookmarkService.bookmarkCreateRemote(
      accountID = this.accountId,
      bookmark = bookmark
    )
  }

  private fun showErrorWithRunnable(
    context: Context,
    title: String,
    failure: Exception,
    execute: () -> Unit
  ) {
    this.log.error("error: {}: ", title, failure)

    this.uiThread.runOnUIThread {
      AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(failure.localizedMessage)
        .setOnDismissListener {
          execute.invoke()
        }
        .show()
    }
  }
}
