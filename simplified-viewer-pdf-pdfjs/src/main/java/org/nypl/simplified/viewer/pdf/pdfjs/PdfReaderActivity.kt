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
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
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
  private lateinit var pdfTitle: TextView
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
    this.pdfTitle = findViewById(R.id.pdf_title)
    this.pdfTitle.text = params.documentTitle

    this.accountId = params.accountId
    this.feedEntry = params.entry
    this.bookID = params.id

    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)

    val backgroundThread = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))

    backgroundThread.execute {
      restoreSavedPosition(
        params = params,
        isSavedInstanceStateNull = savedInstanceState == null
      )
    }
  }

  private fun completeReaderSetup(params: PdfReaderParameters, isSavedInstanceStateNull: Boolean) {
    this.loadingBar.visibility = View.GONE

    if (isSavedInstanceStateNull) {
      createWebView()
      createPdfServer(params.drmInfo, params.pdfFile)
    }
  }

  private fun restoreSavedPosition(params: PdfReaderParameters, isSavedInstanceStateNull: Boolean) {
    val bookmarks =
      PdfReaderBookmarks.loadBookmarks(
        bookmarkService = this.bookmarkService,
        accountID = this.accountId,
        bookID = this.bookID
      )

    val lastReadBookmarks = bookmarks
      .filterIsInstance<Bookmark.PDFBookmark>()
      .filter { bookmark ->
        bookmark.kind == BookmarkKind.BookmarkLastReadLocation
      }

    this.uiThread.runOnUIThread {

      try {
        // if there's more than one last read bookmark, we'll need to compare their dates
        if (lastReadBookmarks.size > 1) {

          val localLastReadBookmark = lastReadBookmarks.first()
          val serverLastReadBookmark = lastReadBookmarks.last()

          if (serverLastReadBookmark.time.isAfter(localLastReadBookmark.time) &&
            localLastReadBookmark.pageNumber != serverLastReadBookmark.pageNumber
          ) {
            showBookmarkPrompt(
              localLastReadBookmark = localLastReadBookmark,
              serverLastReadBookmark = serverLastReadBookmark,
              params = params,
              isSavedInstanceStateNull = isSavedInstanceStateNull
            )
          } else {
            this.documentPageIndex = lastReadBookmarks.first().pageNumber
            completeReaderSetup(
              params = params,
              isSavedInstanceStateNull = isSavedInstanceStateNull
            )
          }
        } else if (lastReadBookmarks.isNotEmpty()) {
          this.documentPageIndex = lastReadBookmarks.first().pageNumber

          completeReaderSetup(
            params = params,
            isSavedInstanceStateNull = isSavedInstanceStateNull
          )
        } else {
          completeReaderSetup(
            params = params,
            isSavedInstanceStateNull = isSavedInstanceStateNull
          )
        }
      } catch (e: Exception) {
        log.error("Could not get lastReadLocation, defaulting to the 1st page", e)
        completeReaderSetup(
          params = params,
          isSavedInstanceStateNull = isSavedInstanceStateNull
        )
      }
    }
  }

  private fun showBookmarkPrompt(
    localLastReadBookmark: Bookmark.PDFBookmark,
    serverLastReadBookmark: Bookmark.PDFBookmark,
    params: PdfReaderParameters,
    isSavedInstanceStateNull: Boolean
  ) {
    AlertDialog.Builder(this)
      .setTitle(R.string.viewer_position_title)
      .setMessage(R.string.viewer_position_message)
      .setNegativeButton(R.string.viewer_position_move) { dialog, _ ->
        this.documentPageIndex = serverLastReadBookmark.pageNumber
        dialog.dismiss()
        createLocalBookmarkFromPromptAction(
          bookmark = serverLastReadBookmark
        )
        completeReaderSetup(
          params = params,
          isSavedInstanceStateNull = isSavedInstanceStateNull
        )
      }
      .setPositiveButton(R.string.viewer_position_stay) { dialog, _ ->
        this.documentPageIndex = localLastReadBookmark.pageNumber
        dialog.dismiss()
        createLocalBookmarkFromPromptAction(
          bookmark = localLastReadBookmark
        )
        completeReaderSetup(
          params = params,
          isSavedInstanceStateNull = isSavedInstanceStateNull
        )
      }
      .create()
      .show()
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

        @JavascriptInterface
        fun onPageClick() {
          this@PdfReaderActivity.onReaderPageClick()
        }
      },
      "PDFListener"
    )

    this.pdfReaderContainer = findViewById(R.id.pdf_reader_container)
    this.pdfReaderContainer.addView(this.webView)
  }

  private fun createPdfServer(drmInfo: BookDRMInformation, pdfFile: File) {

    val contentProtections = BookContentProtections.create(
      context = this,
      contentProtectionProviders = ServiceLoader.load(ContentProtectionProvider::class.java).toList(),
      drmInfo = drmInfo,
      isManualPassphraseEnabled =
        profilesController.profileCurrent().preferences().isManualLCPPassphraseEnabled,
      onLCPDialogDismissed = {
        finish()
      }
    )

    // Create an immediately-closed socket to get a free port number.
    val ephemeralSocket = ServerSocket(0).apply { close() }

    val createPdfOperation = GlobalScope.async {
      try {
        pdfServer = PdfServer.create(
          contentProtections = contentProtections,
          context = this@PdfReaderActivity,
          drmInfo = drmInfo,
          pdfFile = pdfFile,
          port = ephemeralSocket.localPort
        )
      } catch (exception: Exception) {
        showErrorWithRunnable(
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

      pdfServer?.let {
        it.start()

        webView.loadUrl(
          "http://localhost:${it.port}/assets/pdf-viewer/viewer.html?file=%2Fbook.pdf#page=$documentPageIndex"
        )
      }
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
      onBackPressed()
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

  private fun createLocalBookmarkFromPromptAction(bookmark: Bookmark.PDFBookmark) {
    // we need to create a local bookmark after choosing an option from the prompt because the local
    // bookmark is no longer created when syncing from the server returns a last read location
    // bookmark
    this.bookmarkService.bookmarkCreateLocal(
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
