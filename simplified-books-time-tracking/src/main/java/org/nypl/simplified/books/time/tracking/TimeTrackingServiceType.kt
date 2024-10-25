package org.nypl.simplified.books.time.tracking

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

interface TimeTrackingServiceType : AutoCloseable {

  fun onBookOpenedForTracking(
    accountID: AccountID,
    bookId: PlayerPalaceID,
    libraryId: String,
    timeTrackingUri: URI
  )

  fun onBookClosed()
}
