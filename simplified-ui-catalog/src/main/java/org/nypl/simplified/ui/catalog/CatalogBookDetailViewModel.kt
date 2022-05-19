package org.nypl.simplified.ui.catalog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.google.common.util.concurrent.FluentFuture
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityMatcherType
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import javax.annotation.concurrent.GuardedBy

class CatalogBookDetailViewModel(
  private val feedLoader: FeedLoaderType,
  private val profilesController: ProfilesControllerType,
  private val bookRegistry: BookRegistryType,
  private val buildConfiguration: BuildConfigurationServiceType,
  private val borrowViewModel: CatalogBorrowViewModel,
  private val parameters: CatalogBookDetailFragmentParameters,
  private val listener: FragmentListenerType<CatalogBookDetailEvent>
) : ViewModel(), CatalogPagedViewListener {

  private val instanceId =
    UUID.randomUUID()

  private val logger =
    LoggerFactory.getLogger(CatalogBookDetailViewModel::class.java)

  @GuardedBy("loaderResults")
  private val loaderResults =
    PublishSubject.create<LoaderResultWithArguments>()

  private val subscriptions =
    CompositeDisposable(
      this.bookRegistry.bookEvents()
        .filter { event -> event.bookId == this.parameters.bookID }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onBookStatusEvent),
      this.loaderResults
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onFeedLoaderResult)
    )

  private lateinit var feedArguments: CatalogFeedArguments.CatalogFeedArgumentsRemote

  private val bookWithStatusMutable: MutableLiveData<BookWithStatus> =
    MutableLiveData(this.createBookWithStatus())

  private val bookWithStatus: BookWithStatus
    get() = bookWithStatusMutable.value!!

  private val relatedBooksFeedStateMutable: MutableLiveData<CatalogFeedState?> =
    MutableLiveData(null)

  val relatedBooksFeedState: LiveData<CatalogFeedState?>
    get() = relatedBooksFeedStateMutable

  private data class LoaderResultWithArguments(
    val arguments: CatalogFeedArguments,
    val result: FeedLoaderResult
  )

  private class BookModel(
    val feedEntry: FeedEntry.FeedEntryOPDS,
    val onBookChanged: MutableList<(BookWithStatus) -> Unit> = mutableListOf()
  )

  private val bookModels: MutableMap<BookID, BookModel> =
    mutableMapOf()

  private fun onBookStatusEvent(event: BookStatusEvent) {
    val bookWithStatus = this.createBookWithStatus()
    this.bookWithStatusMutable.value = bookWithStatus
  }

  /*
   * Retrieve the current status of the book, or synthesize a status value based on the
   * OPDS feed entry if the book is not in the registry. The book will only be in the
   * registry if the user has ever tried to borrow it (as per the registry spec).
   */

  private fun createBookWithStatus(): BookWithStatus {
    return this.bookRegistry.bookOrNull(this.parameters.bookID)
      ?: synthesizeBookWithStatus(this.parameters.feedEntry)
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

  override fun onCleared() {
    super.onCleared()
    subscriptions.clear()
  }

  val bookWithStatusLive: LiveData<BookWithStatus>
    get() = bookWithStatusMutable

  val accountProvider = try {
    this.profilesController.profileCurrent()
      .account(this.parameters.feedEntry.accountID)
      .provider
  } catch (e: Exception) {
    this.logger.debug("Couldn't load account provider from profile", e)
    null
  }

  val showDebugBookDetailStatus: Boolean
    get() = this.buildConfiguration.showDebugBookDetailStatus

  val bookCanBeRevoked: Boolean
    get() = try {
      val book = this.bookWithStatus.book
      val profile = this.profilesController.profileCurrent()
      val account = profile.account(book.account)

      if (account.bookDatabase.books().contains(book.id)) {
        when (val status = this.bookWithStatus.status) {
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
    } catch (e: Exception) {
      this.logger.error("could not determine if the book could be revoked: ", e)
      false
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

  val bookCanBeDeleted: Boolean
    get() {
      return try {
        val book = this.bookWithStatus.book
        val profile = this.profilesController.profileCurrent()
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
      } catch (e: Exception) {
        this.logger.error("could not determine if the book could be deleted: ", e)
        false
      }
    }

  override fun openBookDetail(opdsEntry: FeedEntry.FeedEntryOPDS) {
    this.listener.post(
      CatalogBookDetailEvent.OpenBookDetail(this.feedArguments, opdsEntry)
    )
  }

  override fun openViewer(book: Book, format: BookFormat) {
    this.listener.post(CatalogBookDetailEvent.OpenViewer(book, format))
  }

  override fun showTaskError(book: Book, result: TaskResult.Failure<*>) {
    // do nothing
  }

  override fun registerObserver(
    feedEntry: FeedEntry.FeedEntryOPDS,
    callback: (BookWithStatus) -> Unit
  ) {
    this.bookModels.getOrPut(feedEntry.bookID, { BookModel(feedEntry) }).onBookChanged.add(callback)
    this.notifyBookStatus(feedEntry, callback)
  }

  override fun unregisterObserver(
    feedEntry: FeedEntry.FeedEntryOPDS,
    callback: (BookWithStatus) -> Unit
  ) {
    val model = this.bookModels[feedEntry.bookID]
    if (model != null) {
      model.onBookChanged.remove(callback)
      if (model.onBookChanged.isEmpty()) {
        this.bookModels.remove(feedEntry.bookID)
      }
    }
  }

  override fun dismissBorrowError(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryDismissBorrowError(feedEntry.accountID, feedEntry.bookID)
  }

  override fun dismissRevokeError(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryDismissRevokeError(feedEntry.accountID, feedEntry.bookID)
  }

  override fun delete(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryDelete(feedEntry.accountID, feedEntry.bookID)
  }

  override fun borrowMaybeAuthenticated(book: Book) {
    // do nothing
  }

  override fun reserveMaybeAuthenticated(book: Book) {
    // do nothing
  }

  override fun revokeMaybeAuthenticated(book: Book) {
    // do nothing
  }

  fun openFeed(title: String, uri: URI) {
    val arguments =
      CatalogFeedArguments.CatalogFeedArgumentsRemote(
        feedURI = this.feedArguments.feedURI.resolve(uri).normalize(),
        isSearchResults = false,
        ownership = this.feedArguments.ownership,
        title = title
      )

    this.listener.post(
      CatalogBookDetailEvent.OpenFeed(arguments)
    )
  }

  fun loadRelatedBooks(feedRelated: URI) {
    feedArguments =
      this.resolveFeedFromBook(
        accountID = this.bookWithStatus.book.account,
        uri = feedRelated
      )

    this.logger.debug("[{}]: loading remote feed {}", this.instanceId, feedArguments.feedURI)

    val profile =
      this.profilesController.profileCurrent()
    val account =
      profile.account(feedArguments.ownership.accountId)

    /*
     * If the remote feed has an age gate, and we haven't given an age, then display an
     * age gate!
     */

    if (shouldDisplayAgeGate(account.provider.authentication, profile.preferences())) {
      this.logger.debug("[{}]: showing age gate", this.instanceId)
      val newState = CatalogFeedState.CatalogFeedAgeGate(feedArguments)
      this.relatedBooksFeedStateMutable.value = newState
      return
    }

    val loginState =
      account.loginState
    val authentication =
      AccountAuthenticatedHTTP.createAuthorizationIfPresent(loginState.credentials)

    val future =
      this.feedLoader.fetchURI(
        account = account.id,
        uri = feedArguments.feedURI,
        auth = authentication,
        method = "GET"
      )

    this.createNewStatus(
      arguments = feedArguments,
      future = future
    )
  }

  private fun shouldDisplayAgeGate(
    authentication: AccountProviderAuthenticationDescription,
    preferences: ProfilePreferences
  ): Boolean {
    val isCoppa = authentication is AccountProviderAuthenticationDescription.COPPAAgeGate
    return isCoppa && buildConfiguration.showAgeGateUi && preferences.dateOfBirth == null
  }

  /**
   * Create a new feed state for the given operation. The feed is assumed to start in a "loading"
   * state.
   */

  private fun createNewStatus(
    arguments: CatalogFeedArguments,
    future: FluentFuture<FeedLoaderResult>
  ) {
    val newState =
      CatalogFeedState.CatalogFeedLoading(arguments)

    this.relatedBooksFeedStateMutable.value = newState

    /*
     * Register a callback that updates the feed status when the future completes.
     */

    future.map { feedLoaderResult ->
      synchronized(loaderResults) {
        val resultWithArguments =
          LoaderResultWithArguments(arguments, feedLoaderResult)
        this.loaderResults.onNext(resultWithArguments)
      }
    }
  }

  private fun onFeedLoaderResult(resultWithArguments: LoaderResultWithArguments) {
    this.logger.debug(
      "[{}]: feed status updated: {}", this.instanceId,
      resultWithArguments.arguments.javaClass
    )

    this.relatedBooksFeedStateMutable.value =
      this.feedLoaderResultToFeedState(resultWithArguments.arguments, resultWithArguments.result)
  }

  private fun feedLoaderResultToFeedState(
    arguments: CatalogFeedArguments,
    result: FeedLoaderResult
  ): CatalogFeedState {
    return when (result) {
      is FeedLoaderResult.FeedLoaderSuccess ->
        when (val feed = result.feed) {
          is Feed.FeedWithoutGroups ->
            this.onReceivedFeedWithoutGroups(arguments, feed)
          is Feed.FeedWithGroups ->
            this.onReceivedFeedWithGroups(arguments, feed)
        }
      is FeedLoaderResult.FeedLoaderFailure ->
        CatalogFeedState.CatalogFeedLoadFailed(
          arguments = arguments,
          failure = result
        )
    }
  }

  private fun notifyBookStatus(
    feedEntry: FeedEntry.FeedEntryOPDS,
    callback: (BookWithStatus) -> Unit
  ) {
    val bookWithStatus =
      this.bookRegistry.bookOrNull(feedEntry.bookID)
        ?: this.synthesizeBookWithStatus(feedEntry)

    callback(bookWithStatus)
  }

  private fun onReceivedFeedWithoutGroups(
    arguments: CatalogFeedArguments,
    feed: Feed.FeedWithoutGroups
  ): CatalogFeedState.CatalogFeedLoaded {
    if (feed.entriesInOrder.isEmpty()) {
      return CatalogFeedState.CatalogFeedLoaded.CatalogFeedEmpty(
        arguments = arguments,
        search = feed.feedSearch,
        title = feed.feedTitle
      )
    }

    /*
     * Construct a paged list for infinitely scrolling feeds.
     */

    val dataSourceFactory =
      CatalogPagedDataSourceFactory(
        feedLoader = this.feedLoader,
        initialFeed = feed,
        ownership = arguments.ownership,
        profilesController = this.profilesController
      )

    val pagedListConfig =
      PagedList.Config.Builder()
        .setEnablePlaceholders(true)
        .setPageSize(50)
        .setMaxSize(250)
        .setPrefetchDistance(25)
        .build()

    val pagedList =
      LivePagedListBuilder(dataSourceFactory, pagedListConfig)
        .build()

    return CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups(
      arguments = arguments,
      entries = pagedList,
      facetsInOrder = feed.facetsOrder,
      facetsByGroup = feed.facetsByGroup,
      search = feed.feedSearch,
      title = feed.feedTitle
    )
  }

  private fun onReceivedFeedWithGroups(
    arguments: CatalogFeedArguments,
    feed: Feed.FeedWithGroups
  ): CatalogFeedState.CatalogFeedLoaded {
    if (feed.size == 0) {
      return CatalogFeedState.CatalogFeedLoaded.CatalogFeedEmpty(
        arguments = arguments,
        search = feed.feedSearch,
        title = feed.feedTitle
      )
    }

    return CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups(
      arguments = arguments,
      feed = feed
    )
  }

  /**
   * Resolve a given URI as a remote feed. The URI, if non-absolute, is resolved against
   * the current feed arguments in order to produce new arguments to load another feed. This
   * method is intended to be called from book detail contexts, where there may not be a
   * feed accessible that has unambiguous account ownership information (ownership can be
   * per-book, and feeds can contain a mix of accounts).
   *
   * @param accountID The account ID that owns the book
   * @param title The title of the target feed
   * @param uri The URI of the target feed
   */

  private fun resolveFeedFromBook(
    accountID: AccountID,
    uri: URI
  ): CatalogFeedArguments.CatalogFeedArgumentsRemote {
    return when (val arguments = this.parameters.feedArguments) {
      is CatalogFeedArguments.CatalogFeedArgumentsRemote ->
        CatalogFeedArguments.CatalogFeedArgumentsRemote(
          feedURI = arguments.feedURI.resolve(uri),
          isSearchResults = false,
          ownership = CatalogFeedOwnership.OwnedByAccount(accountID),
          title = ""
        )

      is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks -> {
        CatalogFeedArguments.CatalogFeedArgumentsRemote(
          feedURI = uri,
          isSearchResults = false,
          ownership = CatalogFeedOwnership.OwnedByAccount(accountID),
          title = ""
        )
      }
    }
  }

  fun dismissBorrowError() {
    this.borrowViewModel.tryDismissBorrowError(
      this.bookWithStatus.book.account,
      this.bookWithStatus.book.id
    )
  }

  fun dismissRevokeError() {
    this.borrowViewModel.tryDismissRevokeError(
      this.bookWithStatus.book.account,
      this.bookWithStatus.book.id
    )
  }

  fun cancelDownload() {
    this.borrowViewModel.tryCancelDownload(
      this.bookWithStatus.book.account,
      this.bookWithStatus.book.id
    )
  }

  fun delete() {
    this.borrowViewModel.tryDelete(
      this.bookWithStatus.book.account,
      this.bookWithStatus.book.id
    )
  }

  fun borrowMaybeAuthenticated() {
    this.openLoginDialogIfNecessary()
    this.borrowViewModel.tryBorrowMaybeAuthenticated(
      this.bookWithStatus.book
    )
  }

  fun reserveMaybeAuthenticated() {
    this.openLoginDialogIfNecessary()
    this.borrowViewModel.tryReserveMaybeAuthenticated(
      this.bookWithStatus.book
    )
  }

  fun revokeMaybeAuthenticated() {
    this.openLoginDialogIfNecessary()
    this.borrowViewModel.tryRevokeMaybeAuthenticated(
      this.bookWithStatus.book
    )
  }

  private fun openLoginDialogIfNecessary() {
    val accountID = this.bookWithStatus.book.account
    if (this.borrowViewModel.isLoginRequired(accountID)) {
      this.listener.post(
        CatalogBookDetailEvent.LoginRequired(accountID)
      )
    }
  }

  fun openViewer(format: BookFormat) {
    this.listener.post(
      CatalogBookDetailEvent.OpenViewer(this.bookWithStatus.book, format)
    )
  }

  fun goUpwards() {
    this.listener.post(CatalogBookDetailEvent.GoUpwards)
  }

  fun showError(result: TaskResult.Failure<*>) {
    this.logger.debug("showing error: {}", this.bookWithStatus.book.id)

    val errorPageParameters = ErrorPageParameters(
      emailAddress = this.buildConfiguration.supportErrorReportEmailAddress,
      body = "",
      subject = this.buildConfiguration.supportErrorReportSubject,
      attributes = result.attributes.toSortedMap(),
      taskSteps = result.steps
    )
    this.listener.post(
      CatalogBookDetailEvent.OpenErrorPage(errorPageParameters)
    )
  }
}
