package org.nypl.simplified.ui.catalog

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.io7m.jfunctional.Some
import com.io7m.jmulticlose.core.CloseableCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.librarysimplified.viewer.preview.BookPreviewActivity
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.Loaned
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetOPDS12Composite
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetOPDS12Single
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityMatcherType
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.taskrecorder.api.TaskStepResolution
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountDetailModel
import org.nypl.simplified.ui.accounts.AccountPickerDialogFragment
import org.nypl.simplified.ui.catalog.CatalogFeedWithGroupsLaneViewHolder.LaneStyle
import org.nypl.simplified.ui.catalog.CatalogPart.BOOKS
import org.nypl.simplified.ui.catalog.CatalogPart.CATALOG
import org.nypl.simplified.ui.catalog.CatalogPart.HOLDS
import org.nypl.simplified.ui.catalog.saml20.CatalogSAML20Activity
import org.nypl.simplified.ui.catalog.saml20.CatalogSAML20Model
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainActivity
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.nypl.simplified.viewer.api.Viewers
import org.nypl.simplified.viewer.spi.ViewerParameters
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.CLEAR_HISTORY
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.REPLACE_TIP
import org.thepalaceproject.opds.client.OPDSClientType
import org.thepalaceproject.opds.client.OPDSFeedHandleWithoutGroupsType
import org.thepalaceproject.opds.client.OPDSState
import org.thepalaceproject.opds.client.OPDSState.Initial
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedEntry
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithGroups
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithoutGroups
import org.thepalaceproject.opds.client.OPDSState.Loading
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The fragment used for the catalog.
 */

sealed class CatalogFragment : Fragment(), MainBackButtonConsumerType, CatalogViewCallbacksType {

  abstract val catalogPart: CatalogPart

  private val logger =
    LoggerFactory.getLogger(CatalogFragment::class.java)

  /**
   * Subscriptions that will be closed when the fragment is detached (ie. when the app is backgrounded).
   */

  private var subscriptions =
    CloseableCollection.create()

  /**
   * Subscriptions that will be closed when the view state changes.
   */

  private var perViewSubscriptions =
    CloseableCollection.create()

  private lateinit var accessibility: AccessibilityServiceType
  private lateinit var bookPreviewRegistry: BookPreviewRegistryType
  private lateinit var bookRegistry: BookRegistryReadableType
  private lateinit var buttonCreator: CatalogButtons
  private lateinit var catalogBookEvents: CatalogBookRegistryEvents
  private lateinit var contentContainer: FrameLayout
  private lateinit var covers: BookCoverProviderType
  private lateinit var feedLoader: FeedLoaderType
  private lateinit var imageLoader: ImageLoaderType
  private lateinit var images: ImageLoaderType
  private lateinit var opdsClient: OPDSClientType
  private lateinit var profiles: ProfilesControllerType
  private lateinit var screenSize: ScreenSizeInformationType
  private lateinit var viewNow: CatalogFeedView

  final override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view =
      inflater.inflate(R.layout.catalog, container, false)

    this.contentContainer =
      view.findViewById(R.id.catalogContentContainer)
    this.viewNow =
      CatalogFeedViewEmpty.create(inflater, this.contentContainer)

    return view
  }

  final override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()
    this.perViewSubscriptions =
      CloseableCollection.create()

    val services =
      Services.serviceDirectory()
    val opdsClients =
      services.requireService(CatalogOPDSClients::class.java)
    this.profiles =
      services.requireService(ProfilesControllerType::class.java)
    this.images =
      services.requireService(ImageLoaderType::class.java)
    this.covers =
      services.requireService(BookCoverProviderType::class.java)
    this.bookRegistry =
      services.requireService(BookRegistryReadableType::class.java)
    this.bookPreviewRegistry =
      services.requireService(BookPreviewRegistryType::class.java)
    this.screenSize =
      services.requireService(ScreenSizeInformationType::class.java)
    this.feedLoader =
      services.requireService(FeedLoaderType::class.java)
    this.imageLoader =
      services.requireService(ImageLoaderType::class.java)
    this.buttonCreator =
      CatalogButtons(this.requireContext())
    this.catalogBookEvents =
      services.requireService(CatalogBookRegistryEvents::class.java)
    this.accessibility =
      services.requireService(AccessibilityServiceType::class.java)

    this.opdsClient =
      opdsClients.clientFor(this.catalogPart)

    this.subscriptions.add(
      this.opdsClient.state.subscribe(this::onStateChanged)
    )
    this.subscriptions.add(
      this.opdsClient.entry.subscribe(this::onEntryChanged)
    )
  }

  final override fun onFeedSelected(
    accountID: AccountID,
    title: String,
    uri: URI
  ) {
    this.logger.debug("onFeedSelected: \"{}\" {}", title, uri)

    try {
      val account =
        this.profiles.profileCurrent()
          .account(accountID)

      this.opdsClient.goTo(
        OPDSClientRequest.NewFeed(
          accountID = accountID,
          uri = uri,
          credentials = account.loginState.credentials,
          historyBehavior = ADD_TO_HISTORY,
          method = "GET"
        )
      )
    } catch (e: Throwable) {
      this.logger.warn("Error fetching account/feed: ", e)
    }
  }

  final override fun onBookSelected(
    entry: FeedEntry.FeedEntryOPDS
  ) {
    UIThread.checkIsUIThread()
    this.opdsClient.goTo(
      OPDSClientRequest.ExistingEntry(
        entry = entry,
        historyBehavior = ADD_TO_HISTORY
      )
    )
  }

  private fun onEntryChanged(
    oldEntry: FeedEntry,
    newEntry: FeedEntry
  ) {
    UIThread.checkIsUIThread()

    if (newEntry is FeedEntry.FeedEntryOPDS) {
      val view = this.viewNow
      if (view is CatalogFeedViewDetails2) {
        val account =
          this.profiles.profileCurrent()
            .account(newEntry.accountID)

        view.bind(account.provider, newEntry)
        this.onLoadRelatedFeed(view, newEntry)
      }
    }
  }

  private fun onLoadRelatedFeed(
    view: CatalogFeedViewDetails2,
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    val relatedOpt = newEntry.feedEntry.related
    if (relatedOpt is Some<URI>) {
      this.logger.debug("Loading related feed ({})", relatedOpt.get())
      val relatedFuture =
        this.feedLoader.fetchURI(
          accountID = newEntry.accountID,
          uri = relatedOpt.get(),
          credentials = null,
          method = "GET"
        )
      relatedFuture.thenAccept { feedResult ->
        view.root.post {
          try {
            view.bindRelatedFeedResult(feedResult)
          } catch (e: Throwable) {
            view.setNoRelatedFeed()
          }
        }
      }
    } else {
      view.setNoRelatedFeed()
    }
  }

  private fun onStateChanged(
    oldState: OPDSState,
    newState: OPDSState
  ) {
    UIThread.checkIsUIThread()

    this.resetPerViewSubscriptions()
    this.contentContainer.removeAllViews()

    when (newState) {
      is OPDSState.Error -> {
        this.onStateChangedToError(newState)
      }

      is Initial -> {
        this.onStateChangedToInitial()
      }

      is Loading -> {
        this.onStateChangedToLoading()
      }

      is LoadedFeedEntry -> {
        this.onStateChangedToDetails(newState)
        this.viewNow.root.postDelayed({ this.viewNow.startFocus() }, 100L)
      }

      is LoadedFeedWithGroups -> {
        this.onStateChangedToGroups(newState)
        this.viewNow.startFocus()
      }

      is LoadedFeedWithoutGroups -> {
        this.onStateChangedToInfinite(newState)
        this.viewNow.startFocus()
      }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    this.viewNow.onConfigurationChanged(newConfig)
  }

  private fun resetPerViewSubscriptions() {
    try {
      this.perViewSubscriptions.close()
    } catch (e: Throwable) {
      this.logger.warn("Failed to close subscriptions: ", e)
    }
    this.perViewSubscriptions = CloseableCollection.create()
  }

  private fun onStateChangedToDetails(
    newState: LoadedFeedEntry
  ) {
    val view =
      CatalogFeedViewDetails2.create(
        callbacks = this,
        container = this.contentContainer,
        covers = this.covers,
        layoutInflater = this.layoutInflater,
        screenSize = this.screenSize,
      )

    when (val entry = this.opdsClient.entry.get()) {
      is FeedEntry.FeedEntryCorrupt -> {
        // Nothing to do.
      }

      is FeedEntry.FeedEntryOPDS -> {
        val services =
          Services.serviceDirectory()
        val account =
          services.requireService(ProfilesControllerType::class.java)
            .profileCurrent()
            .account(entry.accountID)

        view.bind(account.provider, entry)
        this.onLoadRelatedFeed(view, entry)

        /*
         * Subscribe to book status events for this book. We'll route status events to the view
         * itself so that it can update the UI appropriately.
         */

        val catalogBookRegistry =
          services.requireService(CatalogBookRegistryEvents::class.java)

        val statusSubscription =
          catalogBookRegistry.events
            .filter { e -> e.bookId == entry.bookID }
            .filter { e -> e.statusNow != null }
            .subscribe { e ->
              UIThread.checkIsUIThread()
              view.onStatusUpdate(CatalogBookStatus.create(this.bookRegistry, entry))
            }

        this.perViewSubscriptions.add(
          AutoCloseable { statusSubscription.dispose() }
        )

        view.onStatusUpdate(CatalogBookStatus.create(this.bookRegistry, entry))
      }
    }

    /*
     * Subscribe to the entry on the OPDS client. We'll receive the initial
     * entry on subscription.
     */

    this.perViewSubscriptions.add(
      this.opdsClient.entry.subscribe { _, item ->
        when (item) {
          is FeedEntry.FeedEntryCorrupt -> {
            // Nothing sensible to do here.
          }

          is FeedEntry.FeedEntryOPDS -> {
            val services =
              Services.serviceDirectory()
            val account =
              services.requireService(ProfilesControllerType::class.java)
                .profileCurrent()
                .account(item.accountID)

            view.bind(account.provider, item)
          }
        }
      }
    )

    this.switchView(view)
  }

  final override fun onErrorDetailsDisplayRequested(
    error: TaskResult.Failure<*>
  ) {
    try {
      val services =
        Services.serviceDirectory()
      val buildConfig =
        services.requireService(BuildConfigurationServiceType::class.java)

      MainNavigation.openErrorPage(
        activity = this.requireActivity(),
        parameters = ErrorPageParameters(
          emailAddress = buildConfig.supportErrorReportEmailAddress,
          body = "",
          subject = "[palace-error-report]",
          attributes = error.attributes.toSortedMap(),
          taskSteps = error.steps
        )
      )
    } catch (e: Throwable) {
      this.logger.error("Failed to open error page: ", e)
    }
  }

  final override fun onBookRequestDismissError(
    book: Book
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)

    books.bookBorrowFailedDismiss(
      accountID = book.account,
      bookID = book.id
    )
  }

  final override fun onBookRequestBorrowCancel(
    book: Book,
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)

    books.bookBorrowFailedDismiss(
      accountID = book.account,
      bookID = book.id
    )
  }

  @Deprecated("Unclear why this has its own special method.")
  final override fun onBookRequestSAMLDownload(
    status: CatalogBookStatus<BookStatus.DownloadWaitingForExternalAuthentication>
  ) {
    val account =
      this.profiles.profileCurrent()
        .account(status.book.account)

    CatalogSAML20Model.start(
      account = account,
      book = status.book.id,
      downloadURI = status.status.downloadURI
    )

    try {
      val activity = this.requireActivity()
      val intent = Intent(activity, CatalogSAML20Activity::class.java)
      activity.startActivity(intent)
    } catch (e: Throwable) {
      this.logger.error("Failed to start activity: ", e)
    }
  }

  final override fun onBookRequestBorrow(parameters: CatalogBorrowParameters) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val account =
      profiles.profileCurrent()
        .account(parameters.accountID)

    if (this.isLoginRequired(parameters.accountID)) {
      MainNavigation.showLoginDialog(account)

      AccountDetailModel.executeAfterLogin(
        accountID = parameters.accountID,
        runOnLogin = {
          this.logger.debug("User logged in. Continuing borrow.")
          books.bookBorrow(
            accountID = parameters.accountID,
            bookID = parameters.bookID,
            entry = parameters.entry,
            samlDownloadContext = parameters.samlDownloadContext
          )

          UIThread.runOnUIThread {
            MainNavigation.requestTabChangeForPart(this.catalogPart)
            MainNavigation.Settings.goUp()
          }
        }
      )
    } else {
      books.bookBorrow(
        accountID = parameters.accountID,
        bookID = parameters.bookID,
        entry = parameters.entry,
        samlDownloadContext = parameters.samlDownloadContext
      )
    }
  }

  /**
   * @return `true` if a login is required on the given account
   */

  private fun isLoginRequired(
    accountID: AccountID
  ): Boolean {
    return try {
      val services = Services.serviceDirectory()
      val profiles = services.requireService(ProfilesControllerType::class.java)
      val profile = profiles.profileCurrent()
      val account = profile.account(accountID)
      val requiresLogin = account.requiresCredentials
      val isNotLoggedIn = account.loginState !is AccountLoginState.AccountLoggedIn
      requiresLogin && isNotLoggedIn
    } catch (e: Exception) {
      this.logger.debug("could not retrieve account: ", e)
      false
    }
  }

  final override fun onBookRequestPreviewOpen(
    book: Book
  ) {
    try {
      BookPreviewActivity.startActivity(
        this.requireActivity(),
        FeedEntry.FeedEntryOPDS(
          book.account,
          book.entry
        )
      )
    } catch (e: Throwable) {
      this.logger.error("Failed to start activity: ", e)
    }
  }

  final override fun onBookCanBeRevoked(
    book: Book,
    status: BookStatus
  ): Boolean {
    return try {
      val services =
        Services.serviceDirectory()
      val profiles =
        services.requireService(ProfilesControllerType::class.java)

      val profile =
        profiles.profileCurrent()
      val account =
        profile.account(book.account)

      if (account.bookDatabase.books().contains(book.id)) {
        when (status) {
          is BookStatus.Loaned.LoanedDownloaded ->
            status.returnable

          is BookStatus.Loaned.LoanedNotDownloaded ->
            true

          else ->
            false
        }
      } else {
        false
      }
    } catch (e: Throwable) {
      this.logger.debug("Could not determine if the book could be revoked: ", e)
      false
    }
  }

  final override fun onBookRequestDelete(
    book: Book
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)

    books.bookDelete(
      accountID = book.account,
      bookId = book.id
    )
  }

  final override fun onBookRequestRevoke(
    book: Book
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    val accountID =
      book.account
    val account =
      profiles.profileCurrent()
        .account(accountID)

    if (this.isLoginRequired(accountID)) {
      MainNavigation.showLoginDialog(account)

      AccountDetailModel.executeAfterLogin(
        accountID = accountID,
        runOnLogin = {
          this.logger.debug("User logged in. Continuing revoke.")
          books.bookRevoke(
            accountID = accountID,
            bookId = book.id,
          )

          UIThread.runOnUIThread {
            MainNavigation.requestTabChangeForPart(this.catalogPart)
            MainNavigation.Settings.goUp()
          }
        }
      )
    } else {
      books.bookRevoke(
        accountID = accountID,
        bookId = book.id,
      )
    }
  }

  final override fun onBookRequestViewerOpen(
    book: Book,
    bookFormat: BookFormat
  ) {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val profile =
      profiles.profileCurrent()

    val viewerParameters =
      ViewerParameters(
        flags = mapOf(),
        viewerID = UUID.randomUUID(),
        onLoginRequested = { accountID ->
          try {
            this.logger.debug("Bringing main activity to foreground...")
            val activity = this.requireActivity()
            val intent = Intent(activity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            activity.startActivity(intent)

            this.logger.debug("Showing login dialog...")
            MainNavigation.showLoginDialog(profile.account(accountID))
          } catch (e: Throwable) {
            this.logger.error("Unable to open login dialog: ", e)
          }
        }
      )

    try {
      Viewers.openViewer(
        context = this.requireActivity(),
        preferences = viewerParameters,
        book = book,
        format = bookFormat
      )
    } catch (e: Throwable) {
      this.openErrorForBookException(book, e)
    }
  }

  private fun openErrorForBookException(
    book: Book,
    e: Throwable
  ) {
    try {
      val services =
        Services.serviceDirectory()
      val buildConfig =
        services.requireService(BuildConfigurationServiceType::class.java)
      val profiles =
        services.requireService(ProfilesControllerType::class.java)
      val account =
        profiles.profileCurrent()
          .account(book.account)

      val task = TaskRecorder.create()
      task.beginNewStep("Attempting to open book...")
      task.addAttribute("BookID", book.entry.id)
      task.addAttribute("LibraryID", account.provider.id.toString())
      task.addAttribute("Library", account.provider.displayName)
      task.currentStepFailed(
        message = e.message ?: e.javaClass.name,
        errorCode = "error-book-open-failed",
        exception = e,
        extraMessages = listOf()
      )
      val error = task.finishFailure<Unit>()

      MainNavigation.openErrorPage(
        activity = this.requireActivity(),
        parameters = ErrorPageParameters(
          emailAddress = buildConfig.supportErrorReportEmailAddress,
          body = "",
          subject = "[palace-error-report]",
          attributes = error.attributes.toSortedMap(),
          taskSteps = error.steps
        )
      )
    } catch (e: Throwable) {
      this.logger.error("Failed to open error page: ", e)
    }
  }

  private fun onStateChangedToGroups(
    newState: LoadedFeedWithGroups
  ) {
    val feedHandle =
      newState.handle
    val feed =
      feedHandle.feed()

    val view =
      CatalogFeedViewGroups.create(
        callbacks = this,
        container = this.contentContainer,
        layoutInflater = this.layoutInflater,
        screenSize = this.screenSize,
        window = this.requireActivity().window,
      )

    val entriesGroupedAdapter =
      CatalogFeedWithGroupsAdapter(
        covers = this.covers,
        screenSize = this.screenSize,
        laneStyle = LaneStyle.MAIN_GROUPED_FEED_LANE,
        callbacks = this,
      )

    view.listView.adapter = entriesGroupedAdapter
    view.configureTabs(feed)

    /*
     * Configure the toolbar.
     */

    try {
      val account =
        this.profiles.profileCurrent()
          .account(newState.request.accountID)

      val canGoBack =
        this.opdsClient.hasHistory

      view.toolbar.configure(
        account = account,
        canGoBack = this.opdsClient.hasHistory,
        catalogPart = this.catalogPart,
        icon = account.provider.logo,
        imageLoader = this.imageLoader,
        resources = this.resources,
        search = feed.feedSearch,
        title = account.provider.displayName,
      )
    } catch (e: Throwable) {
      // Nothing sensible we can do about this.
    }

    /*
     * The swipe refresh view simply calls the OPDS client, and marks the view as "no longer
     * refreshing" when the returned future completes. This may, of course, be after the
     * fragment is detached and so we just ignore any exceptions that might be caused by this.
     */

    view.swipeRefresh.setOnRefreshListener {
      val future = feedHandle.refresh()
      future.thenAccept {
        view.swipeRefresh.post { view.swipeRefresh.isRefreshing = false }
      }
    }

    /*
     * Subscribe to the list of grouped items on the OPDS client. We'll receive the initial
     * list on subscription.
     */

    this.perViewSubscriptions.add(
      this.opdsClient.entriesGrouped.subscribe { _, items ->
        entriesGroupedAdapter.submitList(items)
      }
    )

    this.switchView(view)
  }

  final override fun onToolbarBackPressed() {
    UIThread.checkIsUIThread()
    this.opdsClient.goBack()
  }

  final override fun onToolbarLogoPressed(
    currentAccount: AccountID
  ) {
    UIThread.checkIsUIThread()

    val dialog =
      AccountPickerDialogFragment.create(
        currentId = currentAccount,
        showAddAccount = true,
        catalogPart = this.catalogPart
      )
    dialog.show(this.childFragmentManager, dialog.tag)
  }

  private fun onStateChangedToInfinite(
    newState: LoadedFeedWithoutGroups
  ) {
    val feedHandle =
      newState.handle
    val feedPositionInitial =
      AtomicReference(feedHandle.scrollPositionGet())
    val feed =
      feedHandle.feed()

    val services =
      Services.serviceDirectory()
    val registry =
      services.requireService(CatalogBookRegistryEvents::class.java)

    val account =
      this.profiles.profileCurrent()
        .account(newState.request.accountID)

    val view =
      CatalogFeedViewInfinite.create(
        callbacks = this,
        catalogPart = this.catalogPart,
        container = this.contentContainer,
        layoutInflater = this.layoutInflater,
        window = this.requireActivity().window,
      )

    val feedSource =
      CatalogFeedPagingSource(feedHandle)

    val feedAdapter =
      CatalogFeedPagingDataAdapter(
        covers = this.covers,
        buttonCreator = this.buttonCreator,
        registryEvents = registry,
        callbacks = this,
      )

    val feedScope =
      CoroutineScope(Dispatchers.Main)

    val job = feedScope.launch {
      val pager =
        Pager(
          config = PagingConfig(pageSize = 50),
          pagingSourceFactory = { feedSource }
        )
      pager.flow.collect { data -> feedAdapter.submitData(data) }
    }

    this.perViewSubscriptions.add(AutoCloseable { job.cancel() })

    try {
      /*
       * Add a scroll listener that saves the scroll position.
       */

      view.listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
          val linearLayoutManager =
            recyclerView.layoutManager as LinearLayoutManager
          val position =
            linearLayoutManager.findFirstVisibleItemPosition()

          this@CatalogFragment.logger.trace("Saving scroll position {}", position)
          feedHandle.scrollPositionSave(position)
        }
      })

      view.listView.adapter = feedAdapter
      view.configureFacets(
        screen = this.screenSize,
        feed = feed
      )

      view.toolbar.configure(
        account = account,
        canGoBack = this.opdsClient.hasHistory,
        catalogPart = this.catalogPart,
        icon = account.provider.logo,
        imageLoader = this.imageLoader,
        resources = this.resources,
        search = feed.feedSearch,
        title = account.provider.displayName,
      )
    } catch (e: Throwable) {
      // Nothing sensible we can do about this.
      this.logger.debug("Failed to configure views: ", e)
    }

    /*
     * The swipe refresh view simply calls the OPDS client, and marks the view as "no longer
     * refreshing" when the returned future completes. This may, of course, be after the
     * fragment is detached and so we just ignore any exceptions that might be caused by this.
     */

    view.swipeRefresh.setOnRefreshListener {
      val future = feedHandle.refresh()
      future.thenAccept {
        view.swipeRefresh.post { view.swipeRefresh.isRefreshing = false }
      }
    }

    /*
     * Show or hide the list with an appropriate message, based on the content of the feed
     * and the current catalog part.
     */

    val context =
      this.requireContext()

    view.configureListVisibility(
      itemCount = feed.size,
      onEmptyMessage = when (this.catalogPart) {
        CATALOG -> context.getString(R.string.feedEmpty)
        BOOKS -> context.getString(R.string.feedWithGroupsEmptyLoaned)
        HOLDS -> context.getString(R.string.feedWithGroupsEmptyHolds)
      }
    )
    this.switchView(view)
    this.announceSearchResults(context, newState, view)

    /*
     * Set up a listener to restore the scroll position. This works around multiple pieces of
     * Android brokenness: Restoring the scroll position when we navigate to a book detail page
     * and back again, and also properly handling scrolling when new feed pages come in. With
     * the current RecyclerView, not explicitly storing and restoring the scroll position results
     * in all kinds of scroll issues when new pages are loaded.
     */

    feedAdapter.addOnPagesUpdatedListener {
      val feedPosition: Int
      val initial = feedPositionInitial.get()
      if (initial == null) {
        feedPosition = feedHandle.scrollPositionGet()
        // this.logger.trace("Restoring scroll position {}", feedPosition)
      } else {
        feedPositionInitial.set(null)
        feedPosition = initial
        // this.logger.trace("Restoring scroll position (initial) {}", feedPosition)
      }
      view.listView.scrollToPosition(feedPosition)
    }

    this.setupRefreshForLocalFeeds(feedHandle)
  }

  private fun announceSearchResults(
    context: Context,
    newState: LoadedFeedWithoutGroups,
    view: CatalogFeedViewInfinite
  ) {
    if (!this.accessibility.spokenFeedbackEnabled) {
      return
    }
    if (!newState.request.isSearch) {
      return
    }
    if (this.catalogPart != CATALOG) {
      return
    }

    if (newState.handle.feed().size == 0) {
      this.accessibility.speak(context.getString(R.string.feedEmpty))
      return
    }

    this.accessibility.speak(context.getString(R.string.feedSearchResultsAvailable))
    view.listView.requestFocus()
  }

  final override fun onIsBookReturnable(
    book: Book
  ): Boolean {
    val profile = this.profiles.profileCurrent()
    val account = profile.account(book.account)

    return try {
      if (account.bookDatabase.books().contains(book.id)) {
        when (val status = BookStatus.fromBook(book)) {
          is Loaned.LoanedDownloaded ->
            status.returnable

          is Loaned.LoanedNotDownloaded ->
            true

          else ->
            false
        }
      } else {
        false
      }
    } catch (_: Exception) {
      false
    }
  }

  override fun onIsBookDeletable(
    book: Book
  ): Boolean {
    return try {
      val profile = this.profiles.profileCurrent()
      val account = profile.account(book.account)
      return if (account.bookDatabase.books().contains(book.id)) {
        book.entry.availability.matchAvailability(
          object : OPDSAvailabilityMatcherType<Boolean, Exception> {
            override fun onHeldReady(availability: OPDSAvailabilityHeldReady): Boolean =
              false

            override fun onHeld(availability: OPDSAvailabilityHeld): Boolean =
              false

            override fun onHoldable(availability: OPDSAvailabilityHoldable): Boolean =
              false

            override fun onLoaned(availability: OPDSAvailabilityLoaned): Boolean =
              availability.revoke.isNone && book.isDownloaded

            override fun onLoanable(availability: OPDSAvailabilityLoanable): Boolean =
              true

            override fun onOpenAccess(availability: OPDSAvailabilityOpenAccess): Boolean =
              true

            override fun onRevoked(availability: OPDSAvailabilityRevoked): Boolean =
              false
          })
      } else {
        false
      }
    } catch (_: Exception) {
      false
    }
  }

  /**
   * For local feeds (such as My Books, Reservations, etc), we want to refresh the feed when
   * a significant event occurs such as a book being added to or deleted from the registry.
   */

  private fun setupRefreshForLocalFeeds(
    feedHandle: OPDSFeedHandleWithoutGroupsType
  ) {
    when (this.catalogPart) {
      CATALOG -> {
        // Nothing to do.
      }

      BOOKS, HOLDS -> {
        val subscription =
          this.catalogBookEvents.events.subscribe { event ->
            when (event) {
              is BookStatusEvent.BookStatusEventChanged -> {
                // Nothing to do.
              }

              is BookStatusEvent.BookStatusEventAdded,
              is BookStatusEvent.BookStatusEventRemoved -> {
                feedHandle.refresh()
              }
            }
          }
        this.perViewSubscriptions.add(AutoCloseable { subscription.dispose() })
      }
    }
  }

  final override fun onSearchSubmitted(
    accountID: AccountID,
    feedSearch: FeedSearch,
    queryText: String
  ) {
    UIThread.checkIsUIThread()
    this.logger.debug("onSearchSubmitted: {}", queryText)

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val credentials =
      this.credentialsOf(accountID)

    val feedSelection =
      when (this.catalogPart) {
        CATALOG -> FeedBooksSelection.BOOKS_FEED_LOANED
        BOOKS -> FeedBooksSelection.BOOKS_FEED_LOANED
        HOLDS -> FeedBooksSelection.BOOKS_FEED_HOLDS
      }

    when (feedSearch) {
      FeedSearch.FeedSearchLocal -> {
        this.opdsClient.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = accountID,
            historyBehavior = CLEAR_HISTORY,
            isSearch = true,
            generator = {
              profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  search = queryText,
                  feedSelection = feedSelection,
                  facetTitleProvider = CatalogOPDSClients.facetTitleProvider,
                  sortBy = CatalogFeedFacetSortModel.facetSelectedPseudoOrDefault(this.catalogPart),
                  filterByAccountID = null
                )
              ).get(10L, TimeUnit.SECONDS)
            }
          )
        )
      }

      is FeedSearch.FeedSearchOpen1_1 -> {
        this.opdsClient.goTo(
          OPDSClientRequest.NewFeed(
            accountID = accountID,
            credentials = credentials,
            historyBehavior = ADD_TO_HISTORY,
            isSearch = true,
            method = "GET",
            uri = feedSearch.search.getQueryURIForTerms(queryText),
          )
        )
      }
    }
  }

  final override fun onFeedFacetSelected(
    feedFacet: FeedFacet
  ) {
    UIThread.checkIsUIThread()

    val feedSelection =
      when (this.catalogPart) {
        CATALOG -> FeedBooksSelection.BOOKS_FEED_LOANED
        BOOKS -> FeedBooksSelection.BOOKS_FEED_LOANED
        HOLDS -> FeedBooksSelection.BOOKS_FEED_HOLDS
      }

    when (feedFacet) {
      is FeedFacetOPDS12Single -> {
        val credentials =
          this.credentialsOf(feedFacet.accountID)

        this.opdsClient.goTo(
          OPDSClientRequest.NewFeed(
            accountID = feedFacet.accountID,
            uri = feedFacet.opdsFacet.uri,
            credentials = credentials,
            historyBehavior = ADD_TO_HISTORY,
            method = "GET"
          )
        )
      }

      is FeedFacetOPDS12Composite -> {
        val credentials =
          this.credentialsOf(feedFacet.accountID)

        this.opdsClient.goTo(
          OPDSClientRequest.ResolvedCompositeOPDS12Facet(
            historyBehavior = ADD_TO_HISTORY,
            facet = feedFacet,
            credentials = credentials,
            method = "GET"
          )
        )
      }

      is FeedFacetPseudo.FilteringForAccount -> {
        this.opdsClient.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = this.currentAccount().id,
            historyBehavior = CLEAR_HISTORY,
            generator = {
              this.profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  search = null,
                  feedSelection = feedSelection,
                  facetTitleProvider = CatalogOPDSClients.facetTitleProvider,
                  sortBy = CatalogFeedFacetSortModel.facetSelectedPseudoOrDefault(this.catalogPart),
                  filterByAccountID = feedFacet.account
                )
              ).get(10L, TimeUnit.SECONDS)
            }
          )
        )
      }

      is FeedFacetPseudo.Sorting -> {
        val filterByAccountID =
          this.findFilterByAccountID()

        this.opdsClient.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = this.currentAccount().id,
            historyBehavior = CLEAR_HISTORY,
            generator = {
              this.profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  search = null,
                  feedSelection = feedSelection,
                  facetTitleProvider = CatalogOPDSClients.facetTitleProvider,
                  sortBy = feedFacet.sortBy,
                  filterByAccountID = filterByAccountID
                )
              ).get(10L, TimeUnit.SECONDS)
            }
          )
        )
      }
    }
  }

  private fun findFilterByAccountID(): AccountID? {
    return if (CatalogFeedFacetFilterModels.filterModel.facets.isNotEmpty()) {
      val filterFacet =
        CatalogFeedFacetFilterModels.filterModel.createResultFacet("")
      if (filterFacet is FeedFacetPseudo.FilteringForAccount) {
        filterFacet.account
      } else {
        null
      }
    } else {
      null
    }
  }

  private fun onStateChangedToError(
    newState: OPDSState.Error
  ) {
    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    val errorPageParameters =
      ErrorPageParameters(
        emailAddress = buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[palace-error-report]",
        attributes = newState.message.attributes.toSortedMap(),
        taskSteps = listOf(
          TaskStep(
            description = "Attempted to load feed.",
            resolution = TaskStepResolution.TaskStepFailed(
              newState.message.message,
              newState.message.exception,
              "?",
              listOf()
            )
          )
        )
      )

    this.switchView(
      CatalogFeedViewError.create(
        layoutInflater = this.layoutInflater,
        this.contentContainer,
        onShowErrorDetails = {
          MainNavigation.openErrorPage(
            activity = this.requireActivity(),
            parameters = errorPageParameters
          )
        },
        onRetry = {
          this.opdsClient.goTo(newState.request.withHistoryBehaviour(REPLACE_TIP))
        }
      )
    )
  }

  private fun onStateChangedToLoading() {
    this.switchView(CatalogFeedViewLoading.create(this.layoutInflater, this.contentContainer))
  }

  private fun onStateChangedToInitial() {
    this.switchView(CatalogFeedViewEmpty.create(this.layoutInflater, this.contentContainer))

    val account =
      this.currentAccount()

    val services =
      Services.serviceDirectory()
    val opdsClients =
      services.requireService(CatalogOPDSClients::class.java)

    opdsClients.goToRootFeedFor(
      catalogPart = this.catalogPart,
      account = account
    )
  }

  private fun credentialsOf(
    id: AccountID
  ): AccountAuthenticationCredentials? {
    return try {
      val profile = this.profiles.profileCurrent()
      profile.account(id).loginState.credentials
    } catch (e: Exception) {
      this.logger.warn("Error fetching credentials: ", e)
      null
    }
  }

  private fun currentAccount(): AccountType {
    val profile =
      this.profiles.profileCurrent()
    val id =
      profile.preferences()
        .mostRecentAccount

    return profile.account(id)
  }

  final override fun onStop() {
    try {
      this.subscriptions.close()
    } catch (e: Throwable) {
      this.logger.debug("Failed to close resources: ", e)
    }

    super.onStop()
  }

  final override fun onBackButtonPressed(): Result {
    if (!this.opdsClient.hasHistory) {
      return BACK_BUTTON_NOT_CONSUMED
    }

    this.onToolbarBackPressed()
    return BACK_BUTTON_CONSUMED
  }

  private fun switchView(
    view: CatalogFeedView
  ) {
    val viewExisting = this.viewNow
    this.viewNow = view
    viewExisting.clear()
  }
}
