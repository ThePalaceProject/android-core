package org.librarysimplified.viewer.pdf.pdfjs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.api.bookmark.SerializedLocatorPage1
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.thepalaceproject.theme.core.PalaceToolbar
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
      context: Activity,
      parameters: PdfReaderParameters
    ) {
      val bundle = Bundle().apply {
        this.putSerializable(this@Companion.PARAMS_ID, parameters)
      }

      val intent = Intent(context, PdfReaderActivity::class.java).apply {
        this.putExtras(bundle)
      }

      context.startActivity(intent)
    }
  }

  private val log: Logger = LoggerFactory.getLogger(PdfReaderActivity::class.java)

  private val services =
    Services.serviceDirectory()
  private val bookmarkService =
    this.services.requireService(BookmarkServiceType::class.java)
  private val profilesController =
    this.services.requireService(ProfilesControllerType::class.java)

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

    val params = this.intent?.getSerializableExtra(PARAMS_ID) as PdfReaderParameters

    this.setContentView(R.layout.pdfjs_reader)
    this.createToolbar(params.documentTitle)

    this.loadingBar = this.findViewById(R.id.pdf_loading_progress)
    this.accountId = params.accountId
    this.feedEntry = params.entry
    this.bookID = params.id

    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, this.accountId.uuid.toString())
    MDC.put(MDCKeys.BOOK_INTERNAL_ID, this.bookID.value())
    MDC.put(MDCKeys.BOOK_TITLE, this.feedEntry.feedEntry.title)
    MDC.put(MDCKeys.BOOK_ID, this.feedEntry.feedEntry.id)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.feedEntry.feedEntry.publisher)
    MDC.put(MDCKeys.BOOK_FORMAT, "application/pdf")
    MDC.remove(MDCKeys.BOOK_DRM)

    this.uiThread =
      this.services.requireService(UIThreadServiceType::class.java)

    val backgroundThread = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))

    backgroundThread.execute {
      this.restoreSavedPosition(
        params = params,
        isSavedInstanceStateNull = savedInstanceState == null
      )
    }
  }

  private fun completeReaderSetup(
    params: PdfReaderParameters,
    isSavedInstanceStateNull: Boolean
  ) {
    this.loadingBar.visibility = View.GONE

    if (isSavedInstanceStateNull) {
      this.createWebView()
      this.createPdfServer(params.drmInfo, params.pdfFile)
    }
  }

  private fun restoreSavedPosition(
    params: PdfReaderParameters,
    isSavedInstanceStateNull: Boolean
  ) {
    val bookmarks =
      PdfReaderBookmarks.loadBookmarks(
        bookmarkService = this.bookmarkService,
        accountID = this.accountId,
        bookID = this.bookID
      )

    val lastReadBookmark =
      bookmarks.filter { bookmark -> bookmark.kind == PdfBookmarkKind.LAST_READ }
        .firstOrNull()

    this.uiThread.runOnUIThread {
      try {
        if (lastReadBookmark != null) {
          this.documentPageIndex = lastReadBookmark.pageNumber
        } else {
          this.documentPageIndex = 1
        }
      } catch (e: Exception) {
        this.log.debug("Could not get lastReadLocation, defaulting to the 1st page", e)
      } finally {
        this.completeReaderSetup(
          params = params,
          isSavedInstanceStateNull = isSavedInstanceStateNull
        )
      }
    }
  }

  private fun createToolbar(title: String) {
    val toolbar = this.findViewById(R.id.pdf_toolbar) as PalaceToolbar
    toolbar.setLogoOnClickListener {
      this.finish()
    }

    this.setSupportActionBar(toolbar)
    this.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    this.supportActionBar?.setDisplayShowHomeEnabled(true)
    this.supportActionBar?.setHomeActionContentDescription(R.string.content_description_back)
    this.supportActionBar?.setHomeButtonEnabled(true)
    this.supportActionBar?.title = title
  }

  private fun createWebView() {
    WebView.setWebContentsDebuggingEnabled(true)

    this.webView = WebView(this).apply {
      this.settings.javaScriptEnabled = true
    }

    this.webView.addJavascriptInterface(
      object {
        @JavascriptInterface
        fun onPageChanged(pageIndex: Int) {
          this@PdfReaderActivity.onReaderPageChanged(pageIndex)
        }

        @JavascriptInterface
        fun onPageClick() {
          this@PdfReaderActivity.onReaderPageClick()
        }
      },
      "PDFListener"
    )

    this.pdfReaderContainer = this.findViewById(R.id.pdf_reader_container)
    this.pdfReaderContainer.addView(this.webView)
  }

  private fun createPdfServer(drmInfo: BookDRMInformation, pdfFile: File) {
    val contentProtections = BookContentProtections.create(
      context = this.application,
      contentProtectionProviders = ServiceLoader.load(ContentProtectionProvider::class.java)
        .toList(),
      drmInfo = drmInfo,
      isManualPassphraseEnabled =
      this.profilesController.profileCurrent().preferences().isManualLCPPassphraseEnabled,
      onLCPDialogDismissed = {
        this.finish()
      }
    )

    // Create an immediately-closed socket to get a free port number.
    val ephemeralSocket = ServerSocket(0).apply { this.close() }

    val createPdfOperation = GlobalScope.async {
      try {
        this@PdfReaderActivity.pdfServer = PdfServer.create(
          contentProtections = contentProtections,
          context = this@PdfReaderActivity.application,
          drmInfo = drmInfo,
          pdfFile = pdfFile,
          port = ephemeralSocket.localPort
        )
      } catch (exception: Exception) {
        this@PdfReaderActivity.showErrorWithRunnable(
          context = this@PdfReaderActivity,
          title = exception.message ?: "",
          failure = exception,
          execute = {
            this@PdfReaderActivity.finish()
          }
        )
      }
    }

    GlobalScope.launch(Dispatchers.Main) {
      createPdfOperation.await()

      this@PdfReaderActivity.pdfServer?.let {
        it.start()

        this@PdfReaderActivity.webView.loadUrl(
          "http://localhost:${it.port}/assets/pdf-viewer/viewer.html?file=%2Fbook.pdf#page=${this@PdfReaderActivity.documentPageIndex}"
        )
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    this.menuInflater.inflate(R.menu.pdf_reader_menu, menu)

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
    this.pdfServer?.stop()
    this.pdfReaderContainer.removeAllViews()
    this.webView.destroy()
    super.onDestroy()
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
      this.onBackPressed()
    }

    return super.onOptionsItemSelected(item)
  }

  private fun onReaderPageClick() {
    this.uiThread.runOnUIThread {
      if (this.supportActionBar?.isShowing == true) {
        this.supportActionBar?.hide()
      } else {
        this.supportActionBar?.show()
      }
    }
  }

  private fun onReaderPageChanged(pageIndex: Int) {
    this.log.debug("onReaderPageChanged {}", pageIndex)

    this.documentPageIndex = pageIndex

    val bookmark =
      SerializedBookmarks.createWithCurrentFormat(
        bookChapterProgress = 0.0,
        bookChapterTitle = "",
        bookProgress = 0.0,
        bookTitle = this.feedEntry.feedEntry.title,
        deviceID = PdfReaderDevices.deviceId(this.profilesController, this.bookID),
        kind = BookmarkKind.BookmarkLastReadLocation,
        location = SerializedLocatorPage1(pageIndex),
        opdsId = this.feedEntry.feedEntry.id,
        time = DateTime.now(),
        uri = null
      )

    this.bookmarkService.bookmarkCreate(
      accountID = this.accountId,
      bookmark = bookmark,
      ignoreRemoteFailures = true
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
      MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(failure.localizedMessage)
        .setOnDismissListener {
          execute.invoke()
        }
        .show()
    }
  }
}
