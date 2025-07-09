package org.librarysimplified.viewer.preview

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate2
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.runBlocking
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2Fragment
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderModel
import org.librarysimplified.r2.views.SR2ReaderViewCommand
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameUnavailable
import org.librarysimplified.r2.views.SR2SearchFragment
import org.librarysimplified.r2.views.SR2TOCFragment
import org.librarysimplified.services.api.Services
import org.librarysimplified.viewer.epub.readium2.Reader2Themes
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.screen.ScreenEdgeToEdgeFix
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.IOException
import java.util.ServiceLoader
import java.util.UUID
import java.util.concurrent.ExecutionException

class BookPreviewActivity : AppCompatActivity(R.layout.activity_book_preview) {

  private val logger =
    LoggerFactory.getLogger(BookPreviewActivity::class.java)

  companion object {

    private const val EXTRA_ENTRY =
      "org.nypl.simplified.viewer.preview.BookPreviewActivity.entry"

    fun startActivity(
      context: Activity,
      feedEntry: FeedEntry.FeedEntryOPDS
    ) {
      val intent = Intent(context, BookPreviewActivity::class.java)
      val bundle = Bundle().apply {
        this.putSerializable(this@Companion.EXTRA_ENTRY, feedEntry)
      }
      intent.putExtras(bundle)
      context.startActivity(intent)
    }
  }

  private val appCompatDelegate: TxContextWrappingDelegate2 by lazy {
    TxContextWrappingDelegate2(super.getDelegate())
  }

  private lateinit var accessibilityService: AccessibilityServiceType
  private lateinit var account: AccountType
  private lateinit var bookFormat: BookFormat
  private lateinit var feedEntry: FeedEntry.FeedEntryOPDS
  private lateinit var loadingProgress: ProgressBar
  private lateinit var previewContainer: FrameLayout
  private lateinit var previewController: BooksPreviewControllerType
  private lateinit var previewRegistry: BookPreviewRegistryType
  private lateinit var profilesController: ProfilesControllerType

  private var boundless: BoundlessServiceType? = null
  private lateinit var root: View
  private var fragmentNow: Fragment? = null
  private var subscriptions: CompositeDisposable = CompositeDisposable()
  private var file: File? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val services =
      Services.serviceDirectory()

    this.profilesController =
      services.requireService(ProfilesControllerType::class.java)
    this.accessibilityService =
      services.requireService(AccessibilityServiceType::class.java)
    this.previewRegistry =
      services.requireService(BookPreviewRegistryType::class.java)
    this.previewController =
      services.requireService(BooksPreviewControllerType::class.java)
    this.boundless =
      services.optionalService(BoundlessServiceType::class.java)

    this.loadingProgress =
      this.findViewById(R.id.loading_progress)
    this.previewContainer =
      this.findViewById(R.id.preview_container)

    this.loadingProgress.max = 100

    val intent = this.intent
    if (intent == null) {
      this.logger.warn("BookPreviewActivity lacks an intent!")
      this.finish()
      return
    }

    val extras = intent.extras
    if (extras == null) {
      this.logger.warn("BookPreviewActivity intent lacks extras!")
      this.finish()
      return
    }

    val entry = extras.getSerializable(EXTRA_ENTRY) as FeedEntry.FeedEntryOPDS?
    if (entry == null) {
      this.logger.warn("BookPreviewActivity intent lacks OPDS entry!")
      this.finish()
      return
    }

    this.feedEntry = entry
    MDC.put(MDCKeys.BOOK_TITLE, this.feedEntry.feedEntry.title)
    MDC.put(MDCKeys.BOOK_ID, this.feedEntry.feedEntry.id)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.feedEntry.feedEntry.publisher)
    MDC.remove(MDCKeys.BOOK_DRM)
    MDC.remove(MDCKeys.BOOK_FORMAT)

    try {
      this.account =
        this.profilesController.profileCurrent()
          .account(this.feedEntry.accountID)
      MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, this.account.provider.id.toString())
    } catch (e: Exception) {
      this.logger.debug("Unable to locate account: ", e)
      this.finish()
      return
    }

    try {
      this.bookFormat =
        this.account.bookDatabase.entry(this.feedEntry.bookID)
          .book
          .findPreferredFormat()!!
    } catch (e: Throwable) {
      this.logger.debug("Unable to locate book format: ", e)
      this.finish()
      return
    }

    this.previewController.handleBookPreviewStatus(this.feedEntry)

    this.root = this.findViewById(R.id.preview_root)
    ScreenEdgeToEdgeFix.edgeToEdge(this.root)
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.preview_container, fragment)
      .commitAllowingStateLoss()
  }

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onStart() {
    super.onStart()

    this.switchFragment(BookPreviewNullFragment())

    /*
     * The book preview status observable is a BehaviorSubject, so subscribing to it will
     * immediately cause the current views to be reconfigured to whatever is the most recent
     * preview status.
     */

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(
      this.previewRegistry.observeBookPreviewStatus()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onNewBookPreviewStatus)
    )
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    return when (val f = this.fragmentNow) {
      is SR2Fragment -> {
        when (f) {
          is SR2ReaderFragment -> {
            super.onBackPressed()
          }
          is SR2SearchFragment -> {
            this.switchFragment(SR2ReaderFragment())
          }
          is SR2TOCFragment -> {
            this.switchFragment(SR2ReaderFragment())
          }
        }
      }
      null -> {
        super.onBackPressed()
      }
      else -> {
        super.onBackPressed()
      }
    }
  }

  @UiThread
  private fun onViewEventReceived(event: SR2ReaderViewEvent) {
    UIThread.checkIsUIThread()

    return when (event) {
      is SR2BookLoadingFailed -> {
        this.onBookLoadingFailed(event.exception)
      }
      is SR2ControllerBecameAvailable -> {
        this.onControllerBecameAvailable()
      }
      is SR2ControllerBecameUnavailable -> {
        // Nothing to do
      }
    }
  }

  private fun onControllerBecameAvailable() {
    this.switchFragment(SR2ReaderFragment())
  }

  @UiThread
  private fun onViewCommandReceived(command: SR2ReaderViewCommand) {
    UIThread.checkIsUIThread()

    return when (command) {
      SR2ReaderViewCommand.SR2ReaderViewNavigationReaderClose -> {
        this.finish()
      }
      SR2ReaderViewCommand.SR2ReaderViewNavigationSearchClose -> {
        this.switchFragment(SR2ReaderFragment())
      }
      SR2ReaderViewCommand.SR2ReaderViewNavigationSearchOpen -> {
        this.switchFragment(SR2SearchFragment())
      }
      SR2ReaderViewCommand.SR2ReaderViewNavigationTOCClose -> {
        this.switchFragment(SR2ReaderFragment())
      }
      SR2ReaderViewCommand.SR2ReaderViewNavigationTOCOpen -> {
        this.switchFragment(SR2TOCFragment())
      }
    }
  }

  @UiThread
  private fun onControllerEvent(event: SR2Event) {
    UIThread.checkIsUIThread()
  }

  private fun onNewBookPreviewStatus(
    previewStatus: BookPreviewStatus
  ) {
    return when (previewStatus) {
      is BookPreviewStatus.HasPreview.Downloading -> {
        val received = previewStatus.currentTotalBytes
        val expected = previewStatus.expectedTotalBytes

        this.logger.debug(
          "Book preview downloading: {} {} {}", received,
          expected, previewStatus.bytesPerSecond
        )

        if (received != null && expected != null) {
          val percent = (received.toDouble() / expected.toDouble()) * 100.0
          this.loadingProgress.isIndeterminate = false
          this.loadingProgress.setProgress(percent.toInt())
        } else {
          this.loadingProgress.isIndeterminate = true
        }
      }

      is BookPreviewStatus.HasPreview.DownloadFailed -> {
        this.logger.debug("Book preview download failed")
        this.handlePreviewDownloadFailed()
      }

      is BookPreviewStatus.HasPreview.Ready.Embedded -> {
        this.logger.debug("Embedded book preview")
        this.loadingProgress.isVisible = false
        this.switchFragment(BookPreviewEmbeddedFragment.newInstance(previewStatus.url.toString()))
      }

      is BookPreviewStatus.HasPreview.Ready.BookPreview -> {
        this.logger.debug("Book preview")
        this.loadingProgress.isVisible = false
        this.subscriptions.add(SR2ReaderModel.controllerEvents.subscribe(this::onControllerEvent))
        this.subscriptions.add(SR2ReaderModel.viewCommands.subscribe(this::onViewCommandReceived))
        this.subscriptions.add(SR2ReaderModel.viewEvents.subscribe(this::onViewEventReceived))
        this.openReader(previewStatus.file)
      }

      is BookPreviewStatus.HasPreview.Ready.AudiobookPreview -> {
        this.logger.debug("Audiobook preview")
        this.loadingProgress.isVisible = false
        this.openPlayer(previewStatus.file)
      }

      else -> {
        // do nothing
      }
    }
  }

  private fun handlePreviewDownloadFailed() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.bookPreviewFailedTitle)
      .setMessage(R.string.bookPreviewFailedMessage)
      .setOnDismissListener { this.onBackPressed() }
      .create()
      .show()
  }

  private fun openPlayer(file: File) {
    this.logger.debug("openPlayer: {}", file)
    this.file = file

    val audiobookPreviewPlayer = BookPreviewAudiobookFragment.newInstance(file, this.feedEntry)
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.preview_container, audiobookPreviewPlayer)
      .commitAllowingStateLoss()
  }

  private fun openReader(file: File) {
    this.logger.debug("openReader: {}", file)
    this.file = file

    UIThread.checkIsUIThread()

    try {
      val profileCurrent =
        this.profilesController.profileCurrent()

      /*
       * Load the most recently configured theme from the profile's preferences.
       */

      val initialTheme =
        Reader2Themes.toSR2(profileCurrent.preferences().readerPreferences)

      /*
       * Instantiate any content protections that might be needed for DRM...
       */

      val contentProtectionProviders =
        ServiceLoader.load(ContentProtectionProvider::class.java)
          .toList()

      val contentProtections =
        BookContentProtections.create(
          boundless = this.boundless,
          context = this.application,
          contentProtectionProviders = contentProtectionProviders,
          drmInfo = BookDRMInformation.None,
          format = this.bookFormat,
          isManualPassphraseEnabled = false,
          onLCPDialogDismissed = {
            this.logger.debug("Dismissed LCP dialog. Shutting down...")
            this.finish()
          }
        )

      this.logger.debug("Opening asset...")
      val assetRetriever =
        AssetRetriever(
          contentResolver = this.contentResolver,
          httpClient = DefaultHttpClient(),
        )

      val rawBookAsset =
        when (val a =
          runBlocking { assetRetriever.retrieve(file) }) {
          is Try.Failure -> throw IOException(a.value.message)
          is Try.Success -> a.value
        }

      SR2ReaderModel.controllerCreate(
        contentProtections = contentProtections,
        bookFile = rawBookAsset,
        bookId = UUID.randomUUID().toString(),
        theme = initialTheme,
        context = this.application,
        controllers = SR2Controllers(),
        bookmarks = listOf()
      )
    } catch (e: Exception) {
      this.onBookLoadingFailed(e)
    }
  }

  /**
   * Loading a book failed.
   */

  @UiThread
  private fun onBookLoadingFailed(
    exception: Throwable
  ) {
    UIThread.checkIsUIThread()

    val actualException =
      if (exception is ExecutionException) {
        exception.cause ?: exception
      } else {
        exception
      }

    MaterialAlertDialogBuilder(this)
      .setTitle(org.librarysimplified.viewer.epub.readium2.R.string.bookOpenFailedTitle)
      .setMessage(
        this.getString(
          org.librarysimplified.viewer.epub.readium2.R.string.bookOpenFailedMessage,
          actualException.javaClass.name,
          actualException.message
        )
      )
      .setOnDismissListener { this.finish() }
      .create()
      .show()
  }
}
