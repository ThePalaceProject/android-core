package org.nypl.simplified.ui.accounts

import android.Manifest
import android.location.Location
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import hu.akarnokd.rxjava2.subjects.UnicastWorkSubject
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountGeoLocation
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountSearchQuery
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.NamedThreadPools
import org.slf4j.LoggerFactory
import java.net.URI

class AccountListRegistryViewModel(private val locationManager: LocationManager) : ViewModel() {

  private val services =
    Services.serviceDirectory()

  private val accountRegistry =
    services.requireService(AccountProviderRegistryType::class.java)

  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val buildConfig =
    services.requireService(BuildConfigurationServiceType::class.java)

  private val backgroundExecutor =
    NamedThreadPools.namedThreadPool(1, "simplified-registry-io", 19)

  private val logger =
    LoggerFactory.getLogger(AccountListRegistryViewModel::class.java)

  private val locationUpdates = BehaviorSubject.create<Unit>()
  private val queries = BehaviorSubject.createDefault("")

  private val subscriptions: CompositeDisposable =
    CompositeDisposable(
      this.accountRegistry.events
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountRegistryEvent),
      this.profilesController.accountEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountEvent),
      /**
       * Here we are hiding Library Finder functionality
       */
//      Observable.combineLatest(queries, locationUpdates) { query, _ -> createQuery(query) }
//        .switchMapCompletable(this::executeQuery)
//        .subscribeOn(Schedulers.io())
//        .onErrorComplete()
//        .subscribe()
    )

  private fun onAccountRegistryEvent(event: AccountProviderRegistryEvent) {
    this.accountRegistryEvents.onNext(event)
  }

  private fun onAccountEvent(event: AccountEvent) {
    this.accountCreationEvents.onNext(event)
  }

  override fun onCleared() {
    super.onCleared()
    this.subscriptions.clear()
    this.backgroundExecutor.shutdown()
  }

  val supportEmailAddress =
    this.buildConfig.supportErrorReportEmailAddress

  val accountRegistryEvents: UnicastWorkSubject<AccountProviderRegistryEvent> =
    UnicastWorkSubject.create()

  val accountCreationEvents: UnicastWorkSubject<AccountEvent> =
    UnicastWorkSubject.create()

  val accountProvidersList: UnicastWorkSubject<List<AccountProviderDescription?>> =
    UnicastWorkSubject.create()

  val accountRegistryStatus: AccountProviderRegistryStatus
    get() = this.accountRegistry.status

  private val displayNoLocationMessage: Subject<Boolean> =
    BehaviorSubject.createDefault(false)
  val displayNoLocationMessageEvents: Observable<Boolean>
    get() = displayNoLocationMessage.hide().distinctUntilChanged()

  private var activeLocation: Location? = null
    set(value) {
      field = value
      locationUpdates.onNext(Unit)
    }

  fun refreshAccountRegistry() {
    this.backgroundExecutor.execute {
      try {
        this.accountRegistry.refresh(
          includeTestingLibraries = this.profilesController
            .profileCurrent()
            .preferences()
            .showTestingLibraries
        )
      } catch (e: Exception) {
        this.logger.debug("failed to refresh registry: ", e)
      }
    }
  }

  fun createAccount(id: URI) {
    this.profilesController.profileAccountCreate(id)
  }

  /**
   * Here we are hiding Library Finder functionality
   */
  @RequiresPermission(value = Manifest.permission.ACCESS_COARSE_LOCATION)
  fun getLocation(hasPermission: Boolean) {
//    if (hasPermission) {
//      tryAndGetLocation()
//        .subscribeOn(Schedulers.io())
//        .observeOn(AndroidSchedulers.mainThread())
//        .subscribe(displayNoLocationMessage::onNext)
//        .let(subscriptions::add)
//    } else {
//      displayNoLocationMessage.onNext(true)
//    }
  }

  /**
   * Return a list of the available account providers. An account provider is available
   * if no account already exists for it in the current profile.
   */

  fun determineAvailableAccountProviderDescriptions() {
    val accountListToDisplay = updateListToDisplay()
    accountProvidersList.onNext(accountListToDisplay)
  }

  private fun updateListToDisplay(): List<AccountProviderDescription?> {
    val usedAccountProviders =
      this.profilesController
        .profileCurrentlyUsedAccountProviders()
        .map { p -> p.toDescription() }

    this.logger.debug("profile is using {} providers", usedAccountProviders.size)

    val availableAccountProviders =
      this.accountRegistry.accountProviderDescriptions()
        .values
        // Palace hides the default account from end users.
        .filter { it.id != accountRegistry.defaultProvider.id }
        .toMutableList()
    availableAccountProviders.removeAll { accountProvider ->
      usedAccountProviders.any { usedAccount ->
        usedAccount.id == accountProvider.id
      }
    }

    val supportedFeaturedLibrariesIds = this.buildConfig.featuredLibrariesIdsList

    val featuredLibrariesList = availableAccountProviders.filter {
      supportedFeaturedLibrariesIds.contains(it.id.toString())
    }.sortedBy { it.title }

    return if (featuredLibrariesList.isNotEmpty()) {
      availableAccountProviders.removeAll(featuredLibrariesList)
      arrayListOf<AccountProviderDescription?>()
        .apply {
          this.addAll(featuredLibrariesList)

          // if we have more libraries than the featured ones, we need to add a space between them
          if (availableAccountProviders.isNotEmpty()) {
            this.add(null)
            this.addAll(availableAccountProviders)
          }
        }
    } else {
      this.logger.debug("returning {} available providers", availableAccountProviders.size)
      availableAccountProviders
    }
  }

  @RequiresPermission(value = Manifest.permission.ACCESS_COARSE_LOCATION)
  private fun tryAndGetLocation(): Single<Boolean> = Single.fromCallable {
    val isNetworkLocationEnabled =
      locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (isNetworkLocationEnabled) {
      activeLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }
    activeLocation == null
  }

  private fun createQuery(query: String) = AccountSearchQuery(
    location = activeLocation?.toAccountGeoLocation(),
    searchQuery = query,
    includeTestingLibraries = this.profilesController
      .profileCurrent()
      .preferences()
      .showTestingLibraries
  )

  private fun executeQuery(query: AccountSearchQuery) = Completable.fromAction {
    accountRegistry.query(query)
  }

  private fun Location.toAccountGeoLocation() = AccountGeoLocation.Coordinates(
    longitude, latitude
  )
}
