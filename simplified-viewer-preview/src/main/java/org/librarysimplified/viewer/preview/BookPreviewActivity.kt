package org.librarysimplified.viewer.preview

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import io.reactivex.disposables.Disposable
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2PageNumberingMode
import org.librarysimplified.r2.api.SR2ScrollingMode
import org.librarysimplified.r2.api.SR2Theme
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2ControllerReference
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationClose
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenSearch
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC
import org.librarysimplified.r2.views.SR2ReaderViewModel
import org.librarysimplified.r2.views.SR2ReaderViewModelFactory
import org.librarysimplified.r2.views.SR2TOCFragment
import org.librarysimplified.r2.views.search.SR2SearchFragment
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.readium.r2.shared.publication.asset.FileAsset
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File

class BookPreviewActivity : AppCompatActivity(R.layout.activity_book_preview) {

  companion object {

    private const val EXTRA_ENTRY = "org.nypl.simplified.viewer.preview.BookPreviewActivity.entry"

    fun startActivity(
      context: Activity,
      feedEntry: FeedEntry.FeedEntryOPDS
    ) {
      val intent = Intent(context, BookPreviewActivity::class.java)
      val bundle = Bundle().apply {
        this.putSerializable(EXTRA_ENTRY, feedEntry)
      }
      intent.putExtras(bundle)
      context.startActivity(intent)
    }
  }

  private val logger = LoggerFactory.getLogger(BookPreviewActivity::class.java)

  private val appCompatDelegate: TxContextWrappingDelegate by lazy {
    TxContextWrappingDelegate(super.getDelegate())
  }

  private val services =
    Services.serviceDirectory()
  private val accessibilityService =
    services.requireService(AccessibilityServiceType::class.java)
  private val uiThread =
    services.requireService(UIThreadServiceType::class.java)

  private val viewModel: BookPreviewViewModel by viewModels(
    factoryProducer = {
      BookPreviewViewModelFactory(
        services
      )
    }
  )

  private lateinit var feedEntry: FeedEntry.FeedEntryOPDS
  private lateinit var loadingProgress: ProgressBar
  private lateinit var previewContainer: FrameLayout
  private lateinit var readerFragment: SR2ReaderFragment
  private lateinit var readerModel: SR2ReaderViewModel
  private lateinit var searchFragment: SR2SearchFragment
  private lateinit var tocFragment: SR2TOCFragment

  private var file: File? = null
  private var viewSubscription: Disposable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    loadingProgress = findViewById(R.id.loading_progress)
    previewContainer = findViewById(R.id.preview_container)

    this.feedEntry = intent.getSerializableExtra(EXTRA_ENTRY) as FeedEntry.FeedEntryOPDS

    MDC.put(MDCKeys.BOOK_TITLE, this.feedEntry.feedEntry.title)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.feedEntry.feedEntry.publisher)
    MDC.remove(MDCKeys.BOOK_DRM)
    MDC.remove(MDCKeys.BOOK_FORMAT)

    this.viewModel.previewStatusLive.observe(this, this::onNewBookPreviewStatus)

    handleFeedEntry()
  }

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onStop() {
    super.onStop()
    this.viewSubscription?.dispose()
  }

  override fun onBackPressed() {
    if (::tocFragment.isInitialized && this.tocFragment.isVisible) {
      this.closeTOC()
    } else if (::searchFragment.isInitialized && this.searchFragment.isVisible) {
      this.closeSearch()
    } else {
      if (file?.exists() == true) {
        file?.delete()
      }
      super.onBackPressed()
    }
  }

  private fun handleFeedEntry() {
    viewModel.handlePreviewStatus(feedEntry)
  }

  private fun onViewEvent(event: SR2ReaderViewEvent) {
    this.uiThread.checkIsUIThread()

    return when (event) {
      SR2ReaderViewNavigationClose -> {
        onBackPressed()
      }
      SR2ReaderViewNavigationOpenTOC -> {
        openTOC()
      }
      is SR2ControllerBecameAvailable -> {
        this.onControllerBecameAvailable(event.reference)
      }
      is SR2BookLoadingFailed -> {
        handlePreviewDownloadFailed()
      }
      SR2ReaderViewNavigationOpenSearch -> {
        openSearch()
      }
    }
  }

  private fun onControllerBecameAvailable(reference: SR2ControllerReference) {
    if (reference.isFirstStartup) {
      val startLocator = reference.controller.bookMetadata.start
      reference.controller.submitCommand(SR2Command.OpenChapter(startLocator))
    } else {
      // Refresh whatever the controller was looking at previously.
      reference.controller.submitCommand(SR2Command.Refresh)
    }
  }

  private fun onNewBookPreviewStatus(previewStatus: BookPreviewStatus) {
    when (previewStatus) {
      is BookPreviewStatus.HasPreview.Downloading -> {
        this.logger.debug(
          "book preview downloading: {} {} {}", previewStatus.currentTotalBytes,
          previewStatus.expectedTotalBytes, previewStatus.bytesPerSecond
        )
      }
      is BookPreviewStatus.HasPreview.DownloadFailed -> {
        this.logger.debug("book preview download failed")
        handlePreviewDownloadFailed()
      }
      is BookPreviewStatus.HasPreview.Ready.Embedded -> {
        this.logger.debug("embedded book preview")
        this.loadingProgress.isVisible = false
        supportFragmentManager.beginTransaction()
          .add(
            R.id.preview_container,
            BookPreviewEmbeddedFragment.newInstance(previewStatus.url.toString())
          )
          .commitAllowingStateLoss()
      }
      is BookPreviewStatus.HasPreview.Ready.BookPreview -> {
        this.logger.debug("book preview")
        this.loadingProgress.isVisible = false
        openReader(previewStatus.file)
      }
      is BookPreviewStatus.HasPreview.Ready.AudiobookPreview -> {
        this.logger.debug("audiobook preview")
        this.loadingProgress.isVisible = false
        openPlayer(previewStatus.file)
      }
      else -> {
        // do nothing
      }
    }
  }

  private fun handlePreviewDownloadFailed() {
    AlertDialog.Builder(this)
      .setTitle(R.string.bookPreviewFailedTitle)
      .setMessage(R.string.bookPreviewFailedMessage)
      .setOnDismissListener { this.onBackPressed() }
      .create()
      .show()
  }

  private fun openPlayer(file: File) {
    this.file = file

    val audiobookPreviewPlayer = BookPreviewAudiobookFragment.newInstance(file, feedEntry)
    this.supportFragmentManager.beginTransaction()
      .add(R.id.preview_container, audiobookPreviewPlayer)
      .commitAllowingStateLoss()
  }

  private fun openReader(file: File) {
    this.file = file

    val parameters = getReaderParameters(file)

    this.readerModel =
      ViewModelProvider(this, SR2ReaderViewModelFactory(parameters))
        .get(SR2ReaderViewModel::class.java)

    this.viewSubscription =
      this.readerModel.viewEvents.subscribe(this::onViewEvent)

    readerFragment = SR2ReaderFragment.create(
      parameters = parameters
    )

    searchFragment = SR2SearchFragment.create(
      parameters = parameters
    )

    tocFragment = SR2TOCFragment.create(
      parameters = parameters
    )

    this.supportFragmentManager.beginTransaction()
      .add(R.id.preview_container, readerFragment)
      .commitAllowingStateLoss()
  }

  private fun openTOC() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("TOC opening")

    val transaction = this.supportFragmentManager.beginTransaction()
      .hide(readerFragment)

    if (tocFragment.isAdded) {
      transaction.show(tocFragment)
    } else {
      transaction.add(R.id.preview_container, tocFragment)
    }

    transaction.commitAllowingStateLoss()
  }

  private fun closeTOC() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("TOC closing")
    this.supportFragmentManager.beginTransaction()
      .hide(this.tocFragment)
      .show(this.readerFragment)
      .commit()
  }

  private fun openSearch() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("Search opening")

    val transaction = this.supportFragmentManager.beginTransaction()
      .hide(readerFragment)

    if (searchFragment.isAdded) {
      transaction.show(searchFragment)
    } else {
      transaction.add(R.id.preview_container, searchFragment)
    }

    transaction.commitAllowingStateLoss()
  }

  private fun closeSearch() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("Search closing")
    this.supportFragmentManager.beginTransaction()
      .hide(this.searchFragment)
      .show(this.readerFragment)
      .commit()
  }

  private fun getReaderParameters(file: File): SR2ReaderParameters {
    return SR2ReaderParameters(
      contentProtections = listOf(),
      controllers = SR2Controllers(),
      bookFile = FileAsset(file),
      bookId = feedEntry.feedEntry.id,
      isPreview = true,
      pageNumberingMode = SR2PageNumberingMode.WHOLE_BOOK,
      scrollingMode = if (this.accessibilityService.spokenFeedbackEnabled) {
        SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
      } else {
        SR2ScrollingMode.SCROLLING_MODE_PAGINATED
      },
      theme = SR2Theme()
    )
  }
}
