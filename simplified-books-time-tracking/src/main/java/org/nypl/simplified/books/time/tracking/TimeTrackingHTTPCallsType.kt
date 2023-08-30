package org.nypl.simplified.books.time.tracking

import org.nypl.simplified.accounts.database.api.AccountType
import java.io.IOException

interface TimeTrackingHTTPCallsType {

  @Throws(IOException::class)
  fun registerTimeTrackingInfo(
    timeTrackingInfo: TimeTrackingInfo,
    account: AccountType
  ): List<TimeTrackingEntry>
}
