package org.nypl.simplified.ui.accounts

import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.events.UISubjectRelay

/**
 * A silly UI abstraction that relays events on the UI thread. This
 * is required because apparently [Observable.observeOn] cannot be trusted.
 */

class AccountEvents private constructor(
  private val relay: UISubjectRelay<AccountEvent>
) {
  companion object {
    fun create(
      profiles: ProfilesControllerType
    ): AccountEvents {
      return AccountEvents(UISubjectRelay.create(profiles.accountEvents()))
    }
  }

  /**
   * A stream of events that are guaranteed to be observed on the UI thread.
   */

  val events: Observable<AccountEvent> =
    this.relay.events
}
