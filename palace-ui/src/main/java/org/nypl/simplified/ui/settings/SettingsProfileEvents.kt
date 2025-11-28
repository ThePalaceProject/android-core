package org.nypl.simplified.ui.settings

import io.reactivex.Observable
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.events.UISubjectRelay

/**
 * A silly UI abstraction that relays events on the UI thread. This
 * is required because apparently [Observable.observeOn] cannot be trusted.
 */

class SettingsProfileEvents private constructor(
  private val relay: UISubjectRelay<ProfileEvent>
) {
  companion object {
    fun create(
      profiles: ProfilesControllerType
    ): SettingsProfileEvents {
      return SettingsProfileEvents(UISubjectRelay.create(profiles.profileEvents()))
    }
  }

  /**
   * A stream of book status events that are guaranteed to be observed on the UI thread.
   */

  val events: Observable<ProfileEvent> =
    this.relay.events
}
