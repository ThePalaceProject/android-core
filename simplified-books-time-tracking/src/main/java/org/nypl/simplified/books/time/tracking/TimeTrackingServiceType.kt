package org.nypl.simplified.books.time.tracking

import org.librarysimplified.audiobook.api.PlayerEvent
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

/**
 * The time tracking service interface.
 */

interface TimeTrackingServiceType {

  fun startTimeTracking(
    accountID: AccountID,
    bookId: String,
    libraryId: String,
    timeTrackingUri: URI?
  )

  fun onPlayerEventReceived(playerEvent: PlayerEvent)

  fun stopTracking()
}
