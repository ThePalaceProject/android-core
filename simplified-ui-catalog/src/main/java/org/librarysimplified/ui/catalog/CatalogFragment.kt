package org.librarysimplified.ui.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.io7m.jfunctional.Some
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.android.schedulers.AndroidSchedulers
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSClientType
import org.thepalaceproject.opds.client.OPDSState
import org.thepalaceproject.opds.client.OPDSState.Error
import org.thepalaceproject.opds.client.OPDSState.Initial
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedEntry
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithGroups
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithoutGroups
import org.thepalaceproject.opds.client.OPDSState.Loading
import java.net.URI

/**
 * The fragment used for the catalog.
 */

sealed class CatalogFragment : Fragment() {

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

  private lateinit var bookPreviewRegistry: BookPreviewRegistryType
  private lateinit var bookRegistry: BookRegistryReadableType
  private lateinit var buttonCreator: CatalogButtons
  private lateinit var contentContainer: FrameLayout
  private lateinit var covers: BookCoverProviderType
  private lateinit var entriesGroupedAdapter: CatalogFeedWithGroupsAdapter
  private lateinit var entriesUngroupedAdapter: CatalogFeedAdapter
  private lateinit var feedLoader: FeedLoaderType
  private lateinit var imageLoader: ImageLoaderType
  private lateinit var images: ImageLoaderType
  private lateinit var opdsClient: OPDSClientType
  private lateinit var profiles: ProfilesControllerType
  private lateinit var screenSize: ScreenSizeInformationType
  private lateinit var uiThread: UIThreadServiceType
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
    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)
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
      CatalogButtons(this.requireContext(), this.screenSize)

    this.opdsClient =
      opdsClients.clientFor(this.catalogPart)

    this.entriesUngroupedAdapter =
      CatalogFeedAdapter(
        covers = this.covers,
        onBookSelected = this::onBookSelected,
        onReachedNearEnd = this::onInfiniteFeedReachedNearEnd
      )
    this.entriesGroupedAdapter =
      CatalogFeedWithGroupsAdapter(
        covers = this.covers,
        onFeedSelected = this::onFeedSelected,
        onBookSelected = this::onBookSelected,
      )

    this.subscriptions.add(
      this.opdsClient.state.subscribe(this::onStateChanged)
    )
    this.subscriptions.add(
      this.opdsClient.entriesUngrouped.subscribe(this::onEntriesUngroupedChanged)
    )
    this.subscriptions.add(
      this.opdsClient.entriesGrouped.subscribe(this::onEntriesGroupedChanged)
    )
    this.subscriptions.add(
      this.opdsClient.entry.subscribe(this::onEntryChanged)
    )
  }

  private fun onFeedSelected(
    accountID: AccountID,
    title: String,
    address: URI
  ) {
    this.logger.debug("onFeedSelected: \"{}\" {}", title, address)

    try {
      val account =
        this.profiles.profileCurrent()
          .account(accountID)

      this.opdsClient.goTo(
        OPDSClientRequest.NewFeed(
          accountID = accountID,
          uri = address,
          credentials = account.loginState.credentials,
          method = "GET"
        )
      )
    } catch (e: Throwable) {
      this.logger.warn("Error fetching account/feed: ", e)
    }
  }

  private fun onInfiniteFeedReachedNearEnd() {
    this.uiThread.checkIsUIThread()
    this.opdsClient.loadMore()
  }

  private fun onBookSelected(
    entry: FeedEntry.FeedEntryOPDS
  ) {
    this.uiThread.checkIsUIThread()
    this.opdsClient.goTo(OPDSClientRequest.ExistingEntry(entry))
  }

  private fun onEntryChanged(
    oldEntry: FeedEntry,
    newEntry: FeedEntry
  ) {
    this.uiThread.checkIsUIThread()

    if (newEntry is FeedEntry.FeedEntryOPDS) {
      val view = this.viewNow
      if (view is CatalogFeedViewDetails) {
        view.bind(newEntry)
        this.onLoadRelatedFeed(view, newEntry)
      }
    }
  }

  private fun onLoadRelatedFeed(
    view: CatalogFeedViewDetails,
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

  private fun onEntriesGroupedChanged(
    oldGroups: List<FeedGroup>,
    newGroups: List<FeedGroup>
  ) {
    this.uiThread.checkIsUIThread()
    this.entriesGroupedAdapter.submitList(newGroups)
  }

  private fun onEntriesUngroupedChanged(
    oldEntries: List<FeedEntry>,
    newEntries: List<FeedEntry>
  ) {
    this.uiThread.checkIsUIThread()
    this.entriesUngroupedAdapter.submitList(newEntries)
  }

  private fun onStateChanged(
    oldState: OPDSState,
    newState: OPDSState
  ) {
    this.uiThread.checkIsUIThread()

    this.resetPerViewSubscriptions()

    this.contentContainer.removeAllViews()
    when (newState) {
      is Error -> {
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
      }

      is LoadedFeedWithGroups -> {
        this.onStateChangedToGroups(newState)
      }

      is LoadedFeedWithoutGroups -> {
        this.onStateChangedToInfinite(newState)
      }
    }
  }

  private fun resetPerViewSubscriptions() {
    try {
      this.perViewSubscriptions.close()
    } catch (e: Throwable) {
      this.logger.warn("Failed to close subscriptions: ", e)
    }
    this.perViewSubscriptions = CloseableCollection.create()
  }

  private fun synthesizeBookWithStatus(
    item: FeedEntry.FeedEntryOPDS
  ): BookWithStatus {
    val book = Book(
      id = item.bookID,
      account = item.accountID,
      cover = null,
      thumbnail = null,
      entry = item.feedEntry,
      formats = listOf()
    )
    val status = BookStatus.fromBook(book)
    this.logger.debug("Synthesizing {} with status {}", book.id, status)
    return BookWithStatus(book, status)
  }

  private fun onStateChangedToDetails(
    newState: LoadedFeedEntry
  ) {
    val view =
      CatalogFeedViewDetails.create(
        buttonCreator = this.buttonCreator,
        container = this.contentContainer,
        covers = this.covers,
        layoutInflater = this.layoutInflater,
        onBookBorrowRequested = this::onBookBorrowRequested,
        onBookCanBeDeleted = this::onBookCanBeDeleted,
        onBookCanBeRevoked = this::onBookCanBeRevoked,
        onBookDeleteRequested = this::onBookDeleteRequested,
        onBookPreviewOpenRequested = this::onBookPreviewOpenRequested,
        onBookReserveRequested = this::onBookReserveRequested,
        onBookResetStatusInitial = this::onBookResetStatusInitial,
        onBookRevokeRequested = this::onBookRevokeRequested,
        onBookSelected = this::onBookSelected,
        onBookViewerOpen = this::onBookViewerOpen,
        onFeedSelected = this::onFeedSelected,
        onToolbarBackPressed = this::onToolbarBackPressed,
        onToolbarLogoPressed = this::onToolbarLogoPressed,
        screenSize = this.screenSize,
        window = this.requireActivity().window,
      )

    when (val entry = this.opdsClient.entry.get()) {
      is FeedEntry.FeedEntryCorrupt -> {
        // Nothing to do.
      }

      is FeedEntry.FeedEntryOPDS -> {
        view.bind(entry)
        this.onLoadRelatedFeed(view, entry)

        val synthBook =
          this.synthesizeBookWithStatus(entry)

        val catalogStatus =
          CatalogBookStatus(
            status = synthBook.status,
            book = synthBook.book,
            previewStatus = this.bookPreviewStatusOf(entry)
          )

        view.onStatusUpdate(catalogStatus)

        /*
         * Subscribe to book status events for this book. We'll route status events to the view
         * itself so that it can update the UI appropriately.
         */

        val statusSubscription =
          this.bookRegistry.bookEvents()
            .filter { e -> e.bookId == entry.bookID }
            .filter { e -> e.statusNow != null }
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { e ->
              val book =
                this.synthesizeBookWithStatus(entry)

              view.onStatusUpdate(
                CatalogBookStatus(
                  status = e.statusNow!!,
                  previewStatus = this.bookPreviewStatusOf(entry),
                  book = book.book
                )
              )
            }

        this.perViewSubscriptions.add(
          AutoCloseable { statusSubscription.dispose() }
        )
      }
    }

    try {
      val account =
        this.profiles.profileCurrent()
          .account(newState.request.entry.accountID)

      val title =
        when (val e = newState.request.entry) {
          is FeedEntry.FeedEntryCorrupt -> ""
          is FeedEntry.FeedEntryOPDS -> e.feedEntry.title
        }

      view.toolbar.configure(
        imageLoader = this.imageLoader,
        accountProvider = account.provider.toDescription(),
        title = title,
        search = null,
        canGoBack = this.opdsClient.hasHistory
      )
    } catch (e: Throwable) {
      // Nothing sensible we can do about this.
    }

    this.viewNow = view
  }

  private fun onBookBorrowRequested(
    parameters: CatalogBorrowParameters
  ) {
    // Nothing yet.
  }

  private fun onBookPreviewOpenRequested(
    status: CatalogBookStatus<*>
  ) {
    // Nothing yet.
  }

  private fun onBookCanBeDeleted(
    status: CatalogBookStatus<*>
  ): Boolean {
    return false
  }

  private fun onBookCanBeRevoked(
    status: CatalogBookStatus<*>
  ): Boolean {
    return false
  }

  private fun onBookDeleteRequested(
    status: CatalogBookStatus<*>
  ) {
    // Nothing yet.
  }

  private fun onBookRevokeRequested(
    status: CatalogBookStatus<*>
  ) {
    // Nothing yet.
  }

  private fun onBookResetStatusInitial(
    status: CatalogBookStatus<*>
  ) {
    // Nothing yet.
  }

  private fun onBookViewerOpen(
    bookFormat: BookFormat
  ) {
    // Nothing yet.
  }

  private fun onBookReserveRequested(
    parameters: CatalogBorrowParameters
  ) {
    // Nothing yet.
  }

  private fun bookPreviewStatusOf(
    entry: FeedEntry.FeedEntryOPDS
  ): BookPreviewStatus {
    return if (!entry.feedEntry.previewAcquisitions.isNullOrEmpty()) {
      BookPreviewStatus.HasPreview()
    } else {
      BookPreviewStatus.None
    }
  }

  private fun onStateChangedToGroups(
    newState: LoadedFeedWithGroups
  ) {
    val view =
      CatalogFeedViewGroups.create(
        container = this.contentContainer,
        layoutInflater = this.layoutInflater,
        onFacetSelected = this::onFacetSelected,
        onSearchSubmitted = this::onSearchSubmitted,
        onToolbarBackPressed = this::onToolbarBackPressed,
        onToolbarLogoPressed = this::onToolbarLogoPressed,
        screenSize = this.screenSize,
        window = this.requireActivity().window,
      )

    view.listView.adapter = this.entriesGroupedAdapter
    view.configureTabs(newState.feed)

    try {
      val account =
        this.profiles.profileCurrent()
          .account(newState.request.accountID)

      view.toolbar.configure(
        imageLoader = this.imageLoader,
        accountProvider = account.provider.toDescription(),
        title = newState.feed.feedTitle,
        search = newState.feed.feedSearch,
        canGoBack = this.opdsClient.hasHistory
      )
    } catch (e: Throwable) {
      // Nothing sensible we can do about this.
    }

    /*
     * The swipe refresh view simply calls the OPDS client, and marks the view as "no longer
     * refreshing" when the returned future completes. This may, of course, be after the
     * fragment is detached and so we just ignore any exceptions that might becaused by this.
     */

    view.swipeRefresh.setOnRefreshListener {
      val future = this.opdsClient.refresh()
      future.thenAccept {
        view.swipeRefresh.post { view.swipeRefresh.isRefreshing = false }
      }
    }

    this.entriesGroupedAdapter.submitList(newState.feed.feedGroupsInOrder)
    this.viewNow = view
  }

  private fun onToolbarBackPressed() {
    this.uiThread.checkIsUIThread()
    this.opdsClient.goBack()
  }

  private fun onToolbarLogoPressed() {
    this.uiThread.checkIsUIThread()
  }

  private fun onStateChangedToInfinite(
    newState: LoadedFeedWithoutGroups
  ) {
    val view =
      CatalogFeedViewInfinite.create(
        container = this.contentContainer,
        layoutInflater = this.layoutInflater,
        onFacetSelected = this::onFacetSelected,
        onSearchSubmitted = this::onSearchSubmitted,
        onToolbarBackPressed = this::onToolbarBackPressed,
        onToolbarLogoPressed = this::onToolbarLogoPressed,
        window = this.requireActivity().window,
      )

    try {
      view.listView.adapter = this.entriesUngroupedAdapter
      view.configureFacets(
        screen = this.screenSize,
        feed = newState.feed,
        sortFacets = true
      )

      val account =
        this.profiles.profileCurrent()
          .account(newState.request.accountID)

      ImageAccountIcons.loadAccountLogoIntoView(
        loader = this.imageLoader.loader,
        account = account.provider.toDescription(),
        defaultIcon = R.drawable.account_default,
        iconView = view.catalogFeedLibraryLogo
      )

      view.catalogFeedLibraryText.text = account.provider.displayName
      view.toolbar.configure(
        imageLoader = this.imageLoader,
        accountProvider = account.provider.toDescription(),
        title = newState.feed.feedTitle,
        search = newState.feed.feedSearch,
        canGoBack = this.opdsClient.hasHistory
      )
    } catch (e: Throwable) {
      // Nothing sensible we can do about this.
    }

    /*
     * The swipe refresh view simply calls the OPDS client, and marks the view as "no longer
     * refreshing" when the returned future completes. This may, of course, be after the
     * fragment is detached and so we just ignore any exceptions that might becaused by this.
     */

    view.swipeRefresh.setOnRefreshListener {
      val future = this.opdsClient.refresh()
      future.thenAccept {
        this.uiThread.runOnUIThread {
          view.swipeRefresh.post { view.swipeRefresh.isRefreshing = false }
        }
      }
    }

    this.entriesUngroupedAdapter.submitList(this.opdsClient.entriesUngrouped.get())
    this.viewNow = view
  }

  private fun onSearchSubmitted(
    feedSearch: FeedSearch,
    queryText: String
  ) {
    this.uiThread.checkIsUIThread()
    this.logger.debug("onSearchSubmitted: {}", queryText)
  }

  private fun onFacetSelected(
    feedFacet: FeedFacet
  ) {
    this.uiThread.checkIsUIThread()
  }

  private fun onStateChangedToError(
    newState: Error
  ) {
    val view = CatalogFeedViewError.create(this.layoutInflater, this.contentContainer)
    this.viewNow = view
  }

  private fun onStateChangedToLoading() {
    val view = CatalogFeedViewLoading.create(this.layoutInflater, this.contentContainer)
    this.viewNow = view
  }

  private fun onStateChangedToInitial() {
    val view = CatalogFeedViewEmpty.create(this.layoutInflater, this.contentContainer)
    this.viewNow = view

    this.opdsClient.goTo(
      OPDSClientRequest.NewFeed(
        accountID = this.profiles.profileCurrent()
          .preferences()
          .mostRecentAccount,
        uri = URI.create("https://ct.thepalaceproject.org/CT0186/groups/"),
        credentials = null,
        method = "GET"
      )
    )
  }

  final override fun onStop() {
    try {
      this.subscriptions.close()
    } catch (e: Throwable) {
      this.logger.debug("Failed to close resources: ", e)
    }

    super.onStop()
  }
}
