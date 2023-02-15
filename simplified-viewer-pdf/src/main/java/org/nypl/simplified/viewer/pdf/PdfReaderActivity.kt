package org.nypl.simplified.viewer.pdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.common.util.concurrent.MoreExecutors
import edu.umn.minitex.pdf.android.api.PdfFragmentListenerType
import edu.umn.minitex.pdf.android.api.TableOfContentsFragmentListenerType
import edu.umn.minitex.pdf.android.api.TableOfContentsItem
import edu.umn.minitex.pdf.android.pdfviewer.PdfViewerFragment
import edu.umn.minitex.pdf.android.pdfviewer.TableOfContentsFragment
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.readium.r2.shared.fetcher.ResourceInputStream
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.ConsoleWarningLogger
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.parser.readium.ReadiumWebPubParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.ServiceLoader
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class PdfReaderActivity :
  AppCompatActivity(),
  PdfFragmentListenerType,
  TableOfContentsFragmentListenerType {

  companion object {
    const val TABLE_OF_CONTENTS = "table_of_contents"

    private const val PARAMS_ID = "edu.umn.minitex.pdf.android.pdfreader.PdfReaderActivity.params"

    /**
     * Factory method to start a [PdfReaderActivity]
     */
    fun startActivity(
      from: Activity,
      parameters: PdfReaderParameters
    ) {
      val b = Bundle()
      b.putSerializable(PARAMS_ID, parameters)
      val i = Intent(from, PdfReaderActivity::class.java)
      i.putExtras(b)
      from.startActivity(i)
    }
  }

  private val log: Logger = LoggerFactory.getLogger(PdfReaderActivity::class.java)

  // vars assigned in onCreate and passed with the intent
  private lateinit var documentTitle: String
  private lateinit var drmInfo: BookDRMInformation
  private lateinit var pdfFile: File
  private lateinit var accountId: AccountID
  private lateinit var id: BookID
  private lateinit var currentProfile: ProfileReadableType
  private lateinit var account: AccountType
  private lateinit var books: BookDatabaseType
  private lateinit var feedEntry: FeedEntry.FeedEntryOPDS
  private lateinit var loadingBar: ProgressBar
  private lateinit var entry: BookDatabaseEntryType
  private lateinit var handle: BookDatabaseEntryFormatHandlePDF
  private lateinit var uiThread: UIThreadServiceType

  // vars for the activity to pass back to the reader or table of contents fragment
  private var documentPageIndex: Int = 0
  private var tableOfContentsList: ArrayList<TableOfContentsItem> = arrayListOf()

  private val services =
    Services.serviceDirectory()
  private val bookmarkService =
    services.requireService(BookmarkServiceType::class.java)
  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val contentProtectionProviders =
    ServiceLoader.load(ContentProtectionProvider::class.java).toList()

  override fun onCreate(savedInstanceState: Bundle?) {
    log.debug("onCreate")
    super.onCreate(savedInstanceState)
    setContentView(R.layout.pdf_reader)

    this.loadingBar = findViewById(R.id.pdf_loading_progress)

    val intentParams = intent?.getSerializableExtra(PARAMS_ID) as PdfReaderParameters
    this.documentTitle = intentParams.documentTile
    this.drmInfo = intentParams.drmInfo
    this.pdfFile = intentParams.pdfFile
    this.accountId = intentParams.accountId
    this.feedEntry = intentParams.entry
    this.id = intentParams.id

    val services =
      Services.serviceDirectory()

    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)

    this.currentProfile =
      services.requireService(ProfilesControllerType::class.java).profileCurrent()
    this.account = currentProfile.account(accountId)
    this.books = account.bookDatabase

    val toolbar = this.findViewById(R.id.pdf_toolbar) as Toolbar
    this.setSupportActionBar(toolbar)
    this.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    this.supportActionBar?.setDisplayShowHomeEnabled(true)
    this.supportActionBar?.setHomeActionContentDescription(R.string.content_description_back)
    this.supportActionBar?.title = ""

    val backgroundThread = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))

    backgroundThread.execute {
      restoreSavedPosition(
        savedInstanceState = savedInstanceState
      )
    }
  }

  private fun restoreSavedPosition(savedInstanceState: Bundle?) {

    val bookmarks =
      PdfReaderBookmarks.loadBookmarks(
        bookmarkService = this.bookmarkService,
        accountID = this.accountId,
        bookID = this.id
      )

    try {

      this.entry = books.entry(id)
      this.handle = entry.findFormatHandle(BookDatabaseEntryFormatHandlePDF::class.java)!!

      val bookMarkLastReadPosition = bookmarks
        .filterIsInstance<Bookmark.PDFBookmark>()
        .find { bookmark ->
          bookmark.kind == BookmarkKind.BookmarkLastReadLocation
        }

      val newPosition = bookMarkLastReadPosition?.pageNumber
        ?: this.handle.format.lastReadLocation!!.pageNumber

      this.uiThread.runOnUIThread {

        if (newPosition != this.documentPageIndex) {
          AlertDialog.Builder(this)
            .setTitle(R.string.viewer_position_title)
            .setMessage(R.string.viewer_position_message)
            .setNegativeButton(R.string.viewer_position_move) { dialog, _ ->
              this.documentPageIndex = newPosition
              completeReaderSetup(
                savedInstanceState = savedInstanceState
              )
              dialog.dismiss()
            }
            .setPositiveButton(R.string.viewer_position_stay) { dialog, _ ->
              completeReaderSetup(
                savedInstanceState = savedInstanceState
              )
              dialog.dismiss()
            }
            .create()
            .show()
        } else {
          completeReaderSetup(
            savedInstanceState = savedInstanceState
          )
        }
      }
    } catch (e: Exception) {
      log.error("Could not get lastReadLocation, defaulting to the 1st page", e)
      this.uiThread.runOnUIThread {
        completeReaderSetup(
          savedInstanceState = savedInstanceState
        )
      }
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    log.debug("onSaveInstanceState")
    outState.putParcelableArrayList(TABLE_OF_CONTENTS, tableOfContentsList)
    super.onSaveInstanceState(outState)
  }

  //region [PdfFragmentListenerType]
  override fun onReaderWantsInputStream(): InputStream {
    log.debug("onReaderWantsInputStream")

    return when (this.drmInfo.kind) {
      BookDRMKind.LCP ->
        try {
          this.lcpInputStream()
        } catch (exception: Exception) {
          showErrorWithRunnable(
            context = this,
            title = exception.message ?: "",
            failure = exception,
            execute = this::finish
          )

          // Return a dummy stream in case the PDF couldn't be opened or decrypted.
          ByteArrayInputStream(ByteArray(0))
        }
      else ->
        pdfFile.inputStream()
    }
  }

  private fun completeReaderSetup(savedInstanceState: Bundle?) {
    if (savedInstanceState == null) {

      this.uiThread.runOnUIThread {
        this.loadingBar.visibility = View.GONE

        // Get the new instance of the reader you want to load here.
        val readerFragment = PdfViewerFragment.newInstance()

        this.supportFragmentManager
          .beginTransaction()
          .replace(R.id.pdf_reader_fragment_holder, readerFragment, "READER")
          .commit()
      }
    } else {
      this.tableOfContentsList =
        savedInstanceState.getParcelableArrayList(TABLE_OF_CONTENTS) ?: arrayListOf()
    }
  }

  private fun lcpInputStream(): InputStream {
    val streamer = Streamer(
      context = this,
      parsers = listOf(
        ReadiumWebPubParser(
          httpClient = DefaultHttpClient(),
          pdfFactory = null
        )
      ),
      contentProtections = BookContentProtections.create(
        context = this,
        contentProtectionProviders = this.contentProtectionProviders,
        drmInfo = this.drmInfo
      ),
      ignoreDefaultParsers = true
    )

    val publication = runBlocking {
      streamer.open(
        asset = FileAsset(pdfFile, MediaType.LCP_PROTECTED_PDF),
        allowUserInteraction = false,
        warnings = ConsoleWarningLogger()
      )
    }.getOrElse {
      throw IOException("Failed to open PDF", it)
    }

    if (publication.isRestricted) {
      throw IOException("Failed to unlock PDF", publication.protectionError)
    }

    // We only support a single PDF file in the archive.
    val link = publication.readingOrder.first()

    return ResourceInputStream(publication.get(link)).buffered(256 * 1024)
  }

  override fun onReaderWantsTitle(): String {
    log.debug("onReaderWantsTitle")
    return this.documentTitle
  }

  override fun onReaderWantsCurrentPage(): Int {
    log.debug("onReaderWantsCurrentPage")
    return this.documentPageIndex
  }

  override fun onReaderPageChanged(pageIndex: Int) {
    log.debug("onReaderPageChanged")
    this.documentPageIndex = pageIndex

    val bookmark = Bookmark.PDFBookmark.create(
      opdsId = this.feedEntry.feedEntry.id,
      time = DateTime.now(),
      kind = BookmarkKind.BookmarkLastReadLocation,
      pageNumber = pageIndex,
      deviceID = PdfReaderDevices.deviceId(this.profilesController, this.id),
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

  override fun onReaderLoadedTableOfContents(tableOfContentsList: ArrayList<TableOfContentsItem>) {
    log.debug("onReaderLoadedTableOfContents. tableOfContentsList: $tableOfContentsList")
    this.tableOfContentsList = tableOfContentsList
  }

  override fun onReaderWantsTableOfContentsFragment() {
    log.debug("onReaderWantsTableOfContentsFragment")

    // Get the new instance of the [TableOfContentsFragment] you want to load here.
    val readerFragment = TableOfContentsFragment.newInstance()

    this.supportFragmentManager
      .beginTransaction()
      .replace(R.id.pdf_reader_fragment_holder, readerFragment, "READER")
      .addToBackStack(null)
      .commit()
  }
  //endregion

  //region [TableOfContentsFragmentListenerType]
  override fun onTableOfContentsWantsItems(): ArrayList<TableOfContentsItem> {
    log.debug("onTableOfContentsWantsItems")
    return this.tableOfContentsList
  }

  override fun onTableOfContentsWantsTitle(): String {
    log.debug("onTableOfContentsWantsTitle")
    return getString(R.string.table_of_contents_title)
  }

  override fun onTableOfContentsWantsEmptyDataText(): String {
    log.debug("onTableOfContentsWantsEmptyDataText")
    return getString(R.string.table_of_contents_empty_message)
  }

  override fun onTableOfContentsItemSelected(pageSelected: Int) {
    log.debug("onTableOfContentsItemSelected. pageSelected: $pageSelected")

    // the reader fragment should be on the backstack and will ask for the page index when `onResume` is called
    this.documentPageIndex = pageSelected
    onBackPressed()
  }
  //endregion

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
