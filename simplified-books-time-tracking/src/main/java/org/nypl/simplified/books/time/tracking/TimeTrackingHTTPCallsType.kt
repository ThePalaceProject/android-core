package org.nypl.simplified.books.time.tracking

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import java.io.IOException

interface TimeTrackingHTTPCallsType {

  @Throws(IOException::class)
  fun registerTimeTrackingInfo(
    timeTrackingInfo: TimeTrackingInfo,
    credentials: AccountAuthenticationCredentials?
  ): List<TimeTrackingEntry>
}
