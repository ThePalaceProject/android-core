package org.nypl.simplified.main

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import hu.akarnokd.rxjava2.subjects.UnicastWorkSubject
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.books.book_registry.BookHoldsUpdateEvent
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacetPseudoTitleProviderType
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.catalog.R
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * The view model for the main fragment.
 */

class MainFragmentViewModel(
  private val resources: Resources
) : ViewModel() {

  companion object {
    // 30 minutes in milliseconds
    private const val REQUEST_HOLDS_INTERVAL = 30 * 60 * 1000L
  }

  val accountEvents: UnicastWorkSubject<AccountEvent> =
    UnicastWorkSubject.create()
  val profileEvents: UnicastWorkSubject<ProfileEvent> =
    UnicastWorkSubject.create()
  val registryEvents: UnicastWorkSubject<BookStatusEvent> =
    UnicastWorkSubject.create()
  val bookHoldEvents: UnicastWorkSubject<BookHoldsUpdateEvent> =
    UnicastWorkSubject.create()

  private val services =
    Services.serviceDirectory()
  val profilesController =
    services.requireService(ProfilesControllerType::class.java)
  val accountProviders: AccountProviderRegistryType =
    services.requireService(AccountProviderRegistryType::class.java)
  val bookRegistry: BookRegistryType =
    services.requireService(BookRegistryType::class.java)
  val buildConfig =
    services.requireService(BuildConfigurationServiceType::class.java)
  val showHoldsTab: Boolean
    get() = buildConfig.showHoldsTab && profilesController.profileCurrent()
      .mostRecentAccount().provider.supportsReservations

  private val subscriptions: CompositeDisposable =
    CompositeDisposable()

  private var holdsCallSubscription: Disposable? = null

  init {
    profilesController.accountEvents()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::onAccountEvent)
      .let { subscriptions.add(it) }
  }

  private fun onAccountEvent(event: AccountEvent) {
    accountEvents.onNext(event)
  }

  init {
    this.profilesController.profileEvents()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::onProfileEvent)
      .let { subscriptions.add(it) }

    this.bookRegistry.bookHoldsUpdateEvents()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::onBookHoldsUpdateEvent)
      .let { subscriptions.add(it) }

    initializeHoldsCallTimer()
  }

  private fun onProfileEvent(event: ProfileEvent) {
    profileEvents.onNext(event)
    initializeHoldsCallTimer()
  }

  private fun onBookHoldsUpdateEvent(event: BookHoldsUpdateEvent) {
    bookHoldEvents.onNext(event)
  }

  private fun initializeHoldsCallTimer() {
    holdsCallSubscription?.dispose()
    holdsCallSubscription = Observable.interval(
      0L, REQUEST_HOLDS_INTERVAL, TimeUnit.MILLISECONDS
    )
      .filter {
        val getHolds = showHoldsTab &&
          profilesController.profileCurrent().mostRecentAccount().loginState is
          AccountLoginState.AccountLoggedIn

        if (!getHolds) {
          bookRegistry.updateHolds(
            numberOfHolds = 0
          )
        }

        getHolds
      }
      .map {
        val booksUri = URI.create("Books")
        val request =
          ProfileFeedRequest(
            facetTitleProvider = CatalogFacetPseudoTitleProvider(this.resources),
            feedSelection = FeedBooksSelection.BOOKS_FEED_HOLDS,
            filterByAccountID = profilesController.profileCurrent().mostRecentAccount().id,
            search = null,
            sortBy = FeedFacet.FeedFacetPseudo.Sorting.SortBy.SORT_BY_TITLE,
            title = "Reservations",
            uri = booksUri
          )

        val numberOfHolds = this.profilesController.profileFeed(request)
          .get()
          .entriesInOrder
          .filter { feedEntry ->
            feedEntry is FeedEntry.FeedEntryOPDS &&
              feedEntry.feedEntry.availability is OPDSAvailabilityHeldReady
          }
          .size

        bookRegistry.updateHolds(
          numberOfHolds = numberOfHolds
        )
      }
      .subscribeOn(Schedulers.io())
      .subscribe()

    subscriptions.add(holdsCallSubscription!!)
  }

  init {
    this.bookRegistry.bookEvents()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::onBookStatusEvent)
      .let { subscriptions.add(it) }
  }

  private fun onBookStatusEvent(event: BookStatusEvent) {
    registryEvents.onNext(event)
  }

  override fun onCleared() {
    super.onCleared()
    subscriptions.clear()
  }

  private class CatalogFacetPseudoTitleProvider(
    val resources: Resources
  ) : FeedFacetPseudoTitleProviderType {
    override val sortByTitle: String
      get() = this.resources.getString(R.string.feedByTitle)
    override val sortByAuthor: String
      get() = this.resources.getString(R.string.feedByAuthor)
    override val collection: String
      get() = this.resources.getString(R.string.feedCollection)
    override val collectionAll: String
      get() = this.resources.getString(R.string.feedCollectionAll)
    override val sortBy: String
      get() = this.resources.getString(R.string.feedSortBy)
    override val show: String
      get() = this.resources.getString(R.string.feedShow)
    override val showAll: String
      get() = this.resources.getString(R.string.feedShowAll)
    override val showOnLoan: String
      get() = this.resources.getString(R.string.feedShowOnLoan)
  }
}
