package org.nypl.simplified.main

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.profiles.controller.api.DeepLinksControllerType
import org.slf4j.LoggerFactory


class DeepLinksController public constructor(
//  private val deepLinkEvents: List<DeepLinkEvent>
) : DeepLinksControllerType {

  private val logger =
    LoggerFactory.getLogger(DeepLinksController::class.java)
  private val observable: PublishSubject<DeepLinkEvent> =
    PublishSubject.create()

  override fun deepLinkEvents(): Observable<DeepLinkEvent> {
    return this.observable
  }

  fun publishDeepLinkEvent(libraryID: AccountID) {
    logger.debug("publishDeepLinkEvent called")
    this.observable.onNext(DeepLinkEvent.DeepLinkIntercepted(
      libraryID = libraryID
    ))
  }

//  companion object {
//    fun create(): BookRegistryType {
//      return BookRegistry(ConcurrentSkipListMap())
//    }
//  }
}
