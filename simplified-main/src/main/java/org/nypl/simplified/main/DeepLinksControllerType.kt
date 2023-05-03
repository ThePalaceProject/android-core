package org.nypl.simplified.profiles.controller.api

import io.reactivex.Observable
import org.nypl.simplified.main.DeepLinkEvent

/**
 * The deep links controller.
 */

interface DeepLinksControllerType {

  /**
   * @return An observable that publishes profile events
   */

  fun deepLinkEvents(): Observable<DeepLinkEvent>

}
