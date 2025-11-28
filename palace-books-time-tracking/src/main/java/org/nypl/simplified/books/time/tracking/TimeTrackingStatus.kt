package org.nypl.simplified.books.time.tracking

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

sealed class TimeTrackingStatus {
  data class Active(
    val accountID: AccountID,
    val bookId: PlayerPalaceID,
    val libraryId: String,
    val timeTrackingUri: URI
  ) : TimeTrackingStatus()

  data object Inactive : TimeTrackingStatus()
}
