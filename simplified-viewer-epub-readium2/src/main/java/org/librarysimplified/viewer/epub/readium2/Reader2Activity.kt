package org.librarysimplified.viewer.epub.readium2

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate2
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkCreated
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2Fragment
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderModel
import org.librarysimplified.r2.views.SR2ReaderViewCommand
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationReaderClose
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationSearchClose
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationSearchOpen
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationTOCClose
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationTOCOpen
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameUnavailable
import org.librarysimplified.r2.views.SR2SearchFragment
import org.librarysimplified.r2.views.SR2TOCFragment
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.AdobeAdeptAssets
import org.nypl.drm.core.AdobeAdeptLoan
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.IOException
import java.util.ServiceLoader
import java.util.concurrent.ExecutionException

class Reader2Activity : AppCompatActivity(R.layout.reader2) {

  private val logger =
    LoggerFactory.getLogger(Reader2Activity::class.java)

  companion object {

    private const val ARG_PARAMETERS =
      "org.librarysimplified.viewer.epub.readium2.Reader2Activity2.parameters"

    /**
     * Start a new reader for the given book.
     */

    fun startActivity(
      activity: Activity,
      parameters: Reader2ActivityParameters
    ) {
      val intent = Intent(activity, Reader2Activity::class.java)
      val bundle = Bundle().apply {
        this.putSerializable(this@Companion.ARG_PARAMETERS, parameters)
      }
      intent.putExtras(bundle)
      activity.startActivity(intent)
    }
  }

  private var fragmentNow: Fragment? = null
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  private lateinit var accessibilityService: AccessibilityServiceType
  private lateinit var analyticsService: AnalyticsType
  private lateinit var bookmarkService: BookmarkServiceType
  private lateinit var parameters: Reader2ActivityParameters
  private lateinit var profilesController: ProfilesControllerType
  private lateinit var uiThread: UIThreadServiceType
  private lateinit var account: AccountType

  private val appCompatDelegate: TxContextWrappingDelegate2 by lazy {
    TxContextWrappingDelegate2(super.getDelegate())
  }

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val services = Services.serviceDirectory()

    this.accessibilityService =
      services.requireService(AccessibilityServiceType::class.java)
    this.analyticsService =
      services.requireService(AnalyticsType::class.java)
    this.bookmarkService =
      services.requireService(BookmarkServiceType::class.java)
    this.profilesController =
      services.requireService(ProfilesControllerType::class.java)
    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)

    this.subscriptions =
      CompositeDisposable()

    val intent = this.intent
    if (intent == null) {
      this.logger.warn("ReaderActivity2 lacks an intent!")
      this.finish()
      return
    }

    val extras = intent.extras
    if (extras == null) {
      this.logger.warn("ReaderActivity2 intent lacks extras!")
      this.finish()
      return
    }

    val params = extras.getSerializable(ARG_PARAMETERS) as Reader2ActivityParameters?
    if (params == null) {
      this.logger.warn("ReaderActivity2 intent lacks parameters!")
      this.finish()
      return
    }

    this.parameters = params
    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, this.parameters.accountId.uuid.toString())
    MDC.put(MDCKeys.BOOK_INTERNAL_ID, this.parameters.bookId.value())
    MDC.put(MDCKeys.BOOK_TITLE, this.parameters.entry.feedEntry.title)
    MDC.put(MDCKeys.BOOK_ID, this.parameters.entry.feedEntry.id)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.parameters.entry.feedEntry.publisher)
    MDC.put(MDCKeys.BOOK_DRM, this.parameters.drmInfo.kind.name)
    MDC.remove(MDCKeys.BOOK_FORMAT)

    try {
      this.account =
        this.profilesController.profileCurrent()
          .account(this.parameters.accountId)
      MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, this.account.provider.id.toString())
    } catch (e: Exception) {
      this.logger.debug("Unable to locate account: ", e)
      this.finish()
      return
    }

    /*
     * Enable webview debugging for debug builds
     */

    if ((this.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      WebView.setWebContentsDebuggingEnabled(true)
    }
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(SR2ReaderModel.controllerEvents.subscribe(this::onControllerEvent))
    this.subscriptions.add(SR2ReaderModel.viewCommands.subscribe(this::onViewCommandReceived))
    this.subscriptions.add(SR2ReaderModel.viewEvents.subscribe(this::onViewEventReceived))

    this.switchFragment(Reader2LoadingFragment())
    this.startReader()
  }

  override fun onStop() {
    super.onStop()

    val fragment = this.fragmentNow
    if (fragment != null) {
      this.supportFragmentManager.beginTransaction()
        .remove(fragment)
        .commitAllowingStateLoss()
    }

    this.subscriptions.dispose()

    /*
     * If the activity is finishing, send an analytics event.
     */

    if (this.isFinishing) {
      val profile = this.profilesController.profileCurrent()

      this.analyticsService.publishEvent(
        AnalyticsEvent.BookClosed(
          timestamp = LocalDateTime.now(),
          credentials = this.account.loginState.credentials,
          profileUUID = profile.id.uuid,
          accountProvider = this.account.provider.id,
          accountUUID = this.account.id.uuid,
          opdsEntry = this.parameters.entry.feedEntry
        )
      )
    }
  }

  /**
   * Start the reader with the given EPUB.
   */

  @UiThread
  private fun startReader() {
    this.uiThread.checkIsUIThread()

    try {
      val profileCurrent =
        this.profilesController.profileCurrent()

      /*
       * Load any bookmarks.
       */

      val bookmarks =
        Reader2Bookmarks.loadBookmarks(
          bookmarkService = this.bookmarkService,
          accountID = this.parameters.accountId,
          bookID = this.parameters.bookId
        )

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

      contentProtectionProviders.forEachIndexed { index, contentProtectionProvider ->
        this.logger.debug("[{}]: Content protection: {}", index, contentProtectionProvider)
      }

      val contentProtections =
        BookContentProtections.create(
          context = this.application,
          contentProtectionProviders = contentProtectionProviders,
          drmInfo = this.parameters.drmInfo,
          isManualPassphraseEnabled = profileCurrent.preferences().isManualLCPPassphraseEnabled,
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
          runBlocking { assetRetriever.retrieve(this@Reader2Activity.parameters.file) }) {
          is Try.Failure -> throw IOException(a.value.message)
          is Try.Success -> a.value
        }

      this.logger.debug("DRM info: {}", this.parameters.drmInfo)
      val bookAsset =
        when (val drmInfo = this.parameters.drmInfo) {
          is BookDRMInformation.LCP ->
            rawBookAsset

          is BookDRMInformation.ACS ->
            this.openWithAdobe(rawBookAsset, drmInfo.rights)

          is BookDRMInformation.AXIS ->
            rawBookAsset

          is BookDRMInformation.None ->
            rawBookAsset
        }

      SR2ReaderModel.controllerCreate(
        contentProtections = contentProtections,
        bookFile = bookAsset,
        bookId = this.parameters.bookId.value(),
        theme = initialTheme,
        context = this.application,
        controllers = SR2Controllers(),
        bookmarks = bookmarks
      )
    } catch (e: Exception) {
      this.onBookLoadingFailed(e)
    }
  }

  @Throws(IOException::class)
  private fun openWithAdobe(
    rawBookAsset: Asset,
    rights: Pair<File, AdobeAdeptLoan>?
  ): Asset {
    if (rawBookAsset !is ContainerAsset) {
      throw IOException("Attempted to open something that is not an EPUB file.")
    }
    if (rights == null) {
      throw IOException("Missing Adobe rights information.")
    }
    return AdobeAdeptAssets.openAsset(
      epubAsset = rawBookAsset,
      rightsFile = rights.first
    )
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.reader2FragmentHost, fragment)
      .commitAllowingStateLoss()
  }

  @UiThread
  private fun onViewEventReceived(event: SR2ReaderViewEvent) {
    this.uiThread.checkIsUIThread()

    return when (event) {
      is SR2BookLoadingFailed -> {
        this.onBookLoadingFailed(event.exception)
      }

      is SR2ControllerBecameAvailable -> {
        this.onControllerBecameAvailable(event.controller)
      }

      is SR2ControllerBecameUnavailable -> {
        // Nothing to do
      }
    }
  }

  @UiThread
  private fun onViewCommandReceived(command: SR2ReaderViewCommand) {
    this.uiThread.checkIsUIThread()

    return when (command) {
      SR2ReaderViewNavigationReaderClose -> {
        this.finish()
      }

      SR2ReaderViewNavigationSearchClose -> {
        this.switchFragment(SR2ReaderFragment())
      }

      SR2ReaderViewNavigationSearchOpen -> {
        this.switchFragment(SR2SearchFragment())
      }

      SR2ReaderViewNavigationTOCClose -> {
        this.switchFragment(SR2ReaderFragment())
      }

      SR2ReaderViewNavigationTOCOpen -> {
        this.switchFragment(SR2TOCFragment())
      }
    }
  }

  @UiThread
  private fun onControllerEvent(event: SR2Event) {
    this.uiThread.checkIsUIThread()

    return when (event) {
      is SR2Event.SR2ThemeChanged -> {
        this.onControllerEventThemeChanged(event)
      }

      is SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionFailed,
      is SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionSucceeded,
      is SR2Event.SR2CommandEvent.SR2CommandSearchResults,
      is SR2Event.SR2CommandEvent.SR2CommandExecutionRunningLong,
      is SR2Event.SR2CommandEvent.SR2CommandExecutionStarted,
      is SR2Event.SR2Error.SR2ChapterNonexistent,
      is SR2Event.SR2Error.SR2WebViewInaccessible,
      is SR2Event.SR2ExternalLinkSelected,
      is SR2Event.SR2OnCenterTapped,
      is SR2Event.SR2ReadingPositionChanged -> {
        // Ignored.
      }

      is SR2BookmarkCreated -> {
        this.onControllerEventBookmarkCreate(event)
      }

      is SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted -> {
        this.onControllerEventBookmarkDelete(event)
      }
    }
  }

  @UiThread
  private fun onControllerEventThemeChanged(event: SR2Event.SR2ThemeChanged) {
    this.uiThread.checkIsUIThread()

    this.profilesController.profileUpdate { current ->
      current.copy(
        preferences = current.preferences.copy(
          readerPreferences = Reader2Themes.fromSR2(event.theme)
        )
      )
    }
  }

  @UiThread
  private fun onControllerEventBookmarkDelete(
    event: SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted
  ) {
    this.uiThread.checkIsUIThread()

    val bookmark =
      Reader2Bookmarks.fromSR2Bookmark(
        bookEntry = this.parameters.entry,
        deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
        source = event.bookmark
      )

    this.bookmarkService.bookmarkDelete(
      accountID = this.account.id,
      bookmark = bookmark,
      ignoreRemoteFailures = true
    )
  }

  @UiThread
  private fun onControllerEventBookmarkCreate(
    event: SR2BookmarkCreated
  ) {
    this.uiThread.checkIsUIThread()

    val localBookmark =
      Reader2Bookmarks.fromSR2Bookmark(
        bookEntry = this.parameters.entry,
        deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
        source = event.bookmark
      )

    when (localBookmark.kind) {
      BookmarkKind.BookmarkExplicit -> this.showToastMessage(R.string.reader_bookmark_added)
      BookmarkKind.BookmarkLastReadLocation -> Unit
    }

    this.bookmarkService.bookmarkCreate(
      accountID = this.parameters.accountId,
      bookmark = localBookmark,
      ignoreRemoteFailures = true
    )
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

      is Reader2LoadingFragment -> {
        super.onBackPressed()
      }

      null -> {
        super.onBackPressed()
      }

      else -> {
        throw IllegalStateException("Unrecognized fragment: $f")
      }
    }
  }

  @UiThread
  private fun onControllerBecameAvailable(controller: SR2ControllerType) {
    this.uiThread.checkIsUIThread()
    this.switchFragment(SR2ReaderFragment())
  }

  /**
   * Loading a book failed.
   */

  @UiThread
  private fun onBookLoadingFailed(
    exception: Throwable
  ) {
    this.uiThread.checkIsUIThread()

    val actualException =
      if (exception is ExecutionException) {
        exception.cause ?: exception
      } else {
        exception
      }

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.bookOpenFailedTitle)
      .setMessage(
        this.getString(
          R.string.bookOpenFailedMessage,
          actualException.javaClass.name,
          actualException.message
        )
      )
      .setOnDismissListener { this.finish() }
      .create()
      .show()
  }

  @UiThread
  private fun showBookmarkPrompt(
    controller: SR2ControllerType,
    localLastReadBookmark: SR2Bookmark,
    serverLastReadBookmark: SR2Bookmark
  ) {
    this.uiThread.checkIsUIThread()

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.reader_position_title)
      .setMessage(R.string.reader_position_message)
      .setNegativeButton(R.string.reader_position_move) { dialog, _ ->
        dialog.dismiss()
        this.createLocalBookmarkFromPromptAction(bookmark = serverLastReadBookmark)
        controller.submitCommand(SR2Command.OpenChapter(serverLastReadBookmark.locator))
      }
      .setPositiveButton(R.string.reader_position_stay) { dialog, _ ->
        dialog.dismiss()
        this.createLocalBookmarkFromPromptAction(bookmark = localLastReadBookmark)
        controller.submitCommand(SR2Command.OpenChapter(localLastReadBookmark.locator))
      }
      .create()
      .show()
  }

  private fun createLocalBookmarkFromPromptAction(
    bookmark: SR2Bookmark
  ) {
    this.bookmarkService.bookmarkCreateLocal(
      accountID = this.parameters.accountId,
      bookmark = Reader2Bookmarks.fromSR2Bookmark(
        bookEntry = this.parameters.entry,
        deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
        source = bookmark
      )
    )
  }

  private fun showToastMessage(@StringRes messageRes: Int) {
    this.uiThread.runOnUIThread {
      Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
  }
}
