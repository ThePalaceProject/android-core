package org.nypl.simplified.accounts.registry;

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.accounts.api.AccountID
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap

/**
 * The deep links controller.
 */

class DeepLinksController private constructor(
  private val deepLinks: ConcurrentSkipListMap<AccountID, DeepLinkEvent>
) : DeepLinksControllerType {

  private val logger =
    LoggerFactory.getLogger(DeepLinksController::class.java)
  private val deepLinksReadOnly: SortedMap<AccountID, DeepLinkEvent> =
    Collections.unmodifiableSortedMap(this.deepLinks)
  private val observable: PublishSubject<DeepLinkEvent> =
    PublishSubject.create()

  fun deepLinksReadOnly(): SortedMap<AccountID, DeepLinkEvent> {
    return this.deepLinksReadOnly
  }

  /**
   * @return An observable that publishes profile events
   */

  override fun deepLinkEvents(): Observable<DeepLinkEvent> {
    return this.observable
  }

  fun publishDeepLinkEvent(accountID: AccountID) {
    this.observable.onNext(
      DeepLinkEvent.DeepLinkIntercepted(
        libraryID = accountID
      )
    )
  }

  companion object {
    fun create(): DeepLinksControllerType {
      return DeepLinksController(ConcurrentSkipListMap())
    }
  }
}
