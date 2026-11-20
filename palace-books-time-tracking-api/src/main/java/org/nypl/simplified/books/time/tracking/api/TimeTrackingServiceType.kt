package org.nypl.simplified.books.time.tracking.api

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

interface TimeTrackingServiceType : AutoCloseable {
  /**
   * Return the current time tracking status.
   *
   * @return The status
   */
  fun status(): TimeTrackingStatus

  fun onBookOpenedForTracking(
    accountID: AccountID,
    bookId: PlayerPalaceID,
    libraryId: String,
    timeTrackingUri: URI,
  )

  fun onBookClosed()
}
