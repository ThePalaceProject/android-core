package org.nypl.simplified.books.time.tracking

import org.librarysimplified.audiobook.api.PlayerEvent
import org.nypl.simplified.accounts.api.AccountID

/**
 * The time tracking service interface.
 */

interface TimeTrackingServiceType {

  fun startTimeTracking(accountId: AccountID, bookId: String)

  fun onPlayerEventReceived(playerEvent: PlayerEvent)

  fun stopTracking()
}
