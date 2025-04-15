package org.nypl.simplified.ui.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.io7m.jfunctional.Some
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.librarysimplified.viewer.preview.BookPreviewActivity
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
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
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountDetailModel
import org.nypl.simplified.ui.accounts.AccountPickerDialogFragment
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.nypl.simplified.viewer.api.Viewers
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSClientType
import org.thepalaceproject.opds.client.OPDSState
import org.thepalaceproject.opds.client.OPDSState.Initial
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedEntry
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithGroups
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithoutGroups
import org.thepalaceproject.opds.client.OPDSState.Loading
import java.net.URI
import java.util.concurrent.TimeUnit

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
      CatalogButtons(this.requireContext(), this.screenSize)

    this.opdsClient =
      opdsClients.clientFor(this.catalogPart)

    this.subscriptions.add(
      this.opdsClient.state.subscribe(this::onStateChanged)
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

  private fun onBookSelected(
    entry: FeedEntry.FeedEntryOPDS
  ) {
    UIThread.checkIsUIThread()
    this.opdsClient.goTo(OPDSClientRequest.ExistingEntry(entry))
  }

  private fun onEntryChanged(
    oldEntry: FeedEntry,
    newEntry: FeedEntry
  ) {
    UIThread.checkIsUIThread()

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
        onToolbarLogoPressed = { this.onToolbarLogoPressed(newState.request.entry.accountID) },
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

        /*
         * Subscribe to book status events for this book. We'll route status events to the view
         * itself so that it can update the UI appropriately.
         */

        val services =
          Services.serviceDirectory()
        val catalogBookRegistry =
          services.requireService(CatalogBookRegistryEvents::class.java)

        val statusSubscription =
          catalogBookRegistry.events
            .filter { e -> e.bookId == entry.bookID }
            .filter { e -> e.statusNow != null }
            .subscribe { e ->
              UIThread.checkIsUIThread()

              val bookWithStatus =
                this.bookRegistry.bookOrNull(e.bookId)
                  ?: this.synthesizeBookWithStatus(entry)

              view.onStatusUpdate(
                CatalogBookStatus(
                  status = bookWithStatus.status,
                  previewStatus = this.bookPreviewStatusOf(entry),
                  book = bookWithStatus.book
                )
              )
            }

        this.perViewSubscriptions.add(
          AutoCloseable { statusSubscription.dispose() }
        )

        val bookWithStatus =
          this.bookRegistry.bookOrNull(entry.bookID)
            ?: this.synthesizeBookWithStatus(entry)

        view.onStatusUpdate(
          CatalogBookStatus(
            status = bookWithStatus.status,
            previewStatus = this.bookPreviewStatusOf(entry),
            book = bookWithStatus.book
          )
        )
      }
    }

    this.onStateChangeToDetailsConfigureToolbar(newState, view)

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

          is FeedEntry.FeedEntryOPDS -> view.bind(item)
        }
      }
    )

    this.switchView(view)
  }

  private fun onStateChangeToDetailsConfigureToolbar(
    newState: LoadedFeedEntry,
    view: CatalogFeedViewDetails
  ) {
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
        accountID = account.id,
        accountProvider = account.provider.toDescription(),
        title = title,
        search = null,
        canGoBack = this.opdsClient.hasHistory,
        catalogPart = this.catalogPart
      )
    } catch (e: Throwable) {
      // Nothing sensible we can do about this.
    }
  }

  private fun onBookBorrowRequested(
    parameters: CatalogBorrowParameters
  ) {
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

  private fun onBookPreviewOpenRequested(
    status: CatalogBookStatus<*>
  ) {
    BookPreviewActivity.startActivity(
      this.requireActivity(),
      FeedEntry.FeedEntryOPDS(
        status.book.account,
        status.book.entry
      )
    )
  }

  /**
   * Determine whether or not a book can be "deleted".
   *
   * A book can be deleted if:
   *
   * * It is loaned, downloaded, and not revocable (because otherwise, a revocation is needed).
   * * It is loanable, but there is a book database entry for it
   * * It is open access but there is a book database entry for it
   */

  private fun onBookCanBeDeleted(
    status: CatalogBookStatus<*>
  ): Boolean {
    return try {
      val services =
        Services.serviceDirectory()
      val profiles =
        services.requireService(ProfilesControllerType::class.java)
      val book =
        status.book
      val profile =
        profiles.profileCurrent()
      val account =
        profile.account(book.account)

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
    } catch (e: Throwable) {
      this.logger.debug("could not determine if the book could be deleted: ", e)
      false
    }
  }

  private fun onBookCanBeRevoked(
    status: CatalogBookStatus<*>
  ): Boolean {
    return try {
      val services =
        Services.serviceDirectory()
      val profiles =
        services.requireService(ProfilesControllerType::class.java)

      val book =
        status.book
      val profile =
        profiles.profileCurrent()
      val account =
        profile.account(book.account)

      if (account.bookDatabase.books().contains(book.id)) {
        when (val s = status.status) {
          is BookStatus.Loaned.LoanedDownloaded ->
            s.returnable
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

  private fun onBookDeleteRequested(
    status: CatalogBookStatus<*>
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)

    books.bookDelete(
      accountID = status.book.account,
      bookId = status.book.id
    )
  }

  private fun onBookRevokeRequested(
    status: CatalogBookStatus<*>
  ) {
    val services =
      Services.serviceDirectory()
    val books =
      services.requireService(BooksControllerType::class.java)

    books.bookRevoke(
      accountID = status.book.account,
      bookId = status.book.id,
      onNewBookEntry = {
        // XXX: What's the correct place to handle this?
      }
    )
  }

  private fun onBookResetStatusInitial(
    status: CatalogBookStatus<*>
  ) {
    // XXX: Unclear what the point of this method ever was...
  }

  private fun onBookViewerOpen(
    book: Book,
    bookFormat: BookFormat
  ) {
    val viewerPreferences =
      ViewerPreferences(
        flags = mapOf()
      )

    Viewers.openViewer(
      context = this.requireActivity(),
      preferences = viewerPreferences,
      book = book,
      format = bookFormat
    )
  }

  private fun onBookReserveRequested(
    parameters: CatalogBorrowParameters
  ) {
    this.onBookBorrowRequested(parameters)
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
    val feedHandle =
      newState.handle
    val feed =
      feedHandle.feed()

    val view =
      CatalogFeedViewGroups.create(
        container = this.contentContainer,
        layoutInflater = this.layoutInflater,
        onFacetSelected = this::onFacetSelected,
        onSearchSubmitted = this::onSearchSubmitted,
        onToolbarBackPressed = this::onToolbarBackPressed,
        onToolbarLogoPressed = { this.onToolbarLogoPressed(newState.request.accountID) },
        screenSize = this.screenSize,
        window = this.requireActivity().window,
      )

    val entriesGroupedAdapter =
      CatalogFeedWithGroupsAdapter(
        covers = this.covers,
        onFeedSelected = this::onFeedSelected,
        onBookSelected = this::onBookSelected,
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

      view.toolbar.configure(
        imageLoader = this.imageLoader,
        accountID = account.id,
        accountProvider = account.provider.toDescription(),
        title = feed.feedTitle,
        search = feed.feedSearch,
        canGoBack = this.opdsClient.hasHistory,
        catalogPart = this.catalogPart
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

  private fun onToolbarBackPressed() {
    UIThread.checkIsUIThread()
    this.opdsClient.goBack()
  }

  private fun onToolbarLogoPressed(
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
    val context =
      this.requireContext()

    val feedHandle =
      newState.handle
    val feed =
      feedHandle.feed()

    val view =
      CatalogFeedViewInfinite.create(
        container = this.contentContainer,
        layoutInflater = this.layoutInflater,
        onFacetSelected = this::onFacetSelected,
        onSearchSubmitted = this::onSearchSubmitted,
        onToolbarBackPressed = this::onToolbarBackPressed,
        onToolbarLogoPressed = { this.onToolbarLogoPressed(newState.request.accountID) },
        window = this.requireActivity().window,
      )

    val entriesUngroupedAdapter =
      CatalogFeedAdapter(
        covers = this.covers,
        onBookSelected = this::onBookSelected,
        onReachedNearEnd = {
          feedHandle.loadMore()
        }
      )

    try {
      view.listView.adapter = entriesUngroupedAdapter
      view.configureFacets(
        screen = this.screenSize,
        feed = feed,
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
        accountID = account.id,
        accountProvider = account.provider.toDescription(),
        title = feed.feedTitle,
        search = feed.feedSearch,
        canGoBack = this.opdsClient.hasHistory,
        catalogPart = this.catalogPart
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
     * Subscribe to the list of feed items in the OPDS client. We'll receive the initial
     * list on subscription.
     */

    this.perViewSubscriptions.add(
      this.opdsClient.entriesUngrouped.subscribe { _, items ->
        entriesUngroupedAdapter.submitList(items)

        /*
         * Show or hide the list with an appropriate message, based on the content of the feed
         * and the current catalog part.
         */

        view.configureListVisibility(
          itemCount = items.size,
          onEmptyMessage = when (this.catalogPart) {
            CatalogPart.CATALOG -> context.getString(R.string.feedEmpty)
            CatalogPart.BOOKS -> context.getString(R.string.feedWithGroupsEmptyLoaned)
            CatalogPart.HOLDS -> context.getString(R.string.feedWithGroupsEmptyHolds)
          }
        )
      }
    )

    this.switchView(view)
  }

  private fun onSearchSubmitted(
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
    val resources =
      MainApplication.application.resources

    val credentials =
      this.credentialsOf(accountID)

    when (feedSearch) {
      FeedSearch.FeedSearchLocal -> {
        this.opdsClient.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = accountID,
            generator = {
              profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = resources.getString(R.string.catalogSearch),
                  search = queryText,
                  facetTitleProvider = CatalogOPDSClients.facetTitleProvider
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
            uri = feedSearch.search.getQueryURIForTerms(queryText),
            credentials = credentials,
            method = "GET"
          )
        )
      }
    }
  }

  private fun onFacetSelected(
    feedFacet: FeedFacet
  ) {
    UIThread.checkIsUIThread()

    when (feedFacet) {
      is FeedFacet.FeedFacetOPDS -> {
        val credentials =
          this.credentialsOf(feedFacet.accountID)

        this.opdsClient.goTo(
          OPDSClientRequest.NewFeed(
            accountID = feedFacet.accountID,
            uri = feedFacet.opdsFacet.uri,
            credentials = credentials,
            method = "GET"
          )
        )
      }

      is FeedFacet.FeedFacetPseudo.FilteringForAccount -> {
        this.opdsClient.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = this.currentAccount().id,
            generator = {
              this.profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = "",
                  facetTitleProvider = CatalogOPDSClients.facetTitleProvider,
                  filterByAccountID = feedFacet.account
                )
              ).get(10L, TimeUnit.SECONDS)
            }
          )
        )
      }

      is FeedFacet.FeedFacetPseudo.Sorting -> {
        this.opdsClient.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = this.currentAccount().id,
            generator = {
              this.profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = "",
                  facetTitleProvider = CatalogOPDSClients.facetTitleProvider,
                  sortBy = feedFacet.sortBy
                )
              ).get(10L, TimeUnit.SECONDS)
            }
          )
        )
      }
    }
  }

  private fun onStateChangedToError(
    newState: OPDSState.Error
  ) {
    this.switchView(CatalogFeedViewError.create(this.layoutInflater, this.contentContainer))
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
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    opdsClients.goToRootFeedFor(
      profiles = profiles,
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

  private fun switchView(
    view: CatalogFeedView
  ) {
    val viewExisting = this.viewNow
    this.viewNow = view
    viewExisting.clear()
  }
}
