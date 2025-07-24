package org.nypl.simplified.tests.mocking

import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.time.tracking.TimeTrackingHTTPCallsType
import org.nypl.simplified.books.time.tracking.TimeTrackingRequest
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponse

class FakeTimeTrackingHTTPCalls : TimeTrackingHTTPCallsType {

  val responses =
    mutableListOf<TimeTrackingServerResponse>()
  val requests =
    mutableListOf<TimeTrackingRequest>()
  val crashes =
    mutableListOf<Exception>()

  override fun registerTimeTrackingInfo(
    request: TimeTrackingRequest,
    account: AccountType
  ): TimeTrackingServerResponse {
    this.requests.add(request)
    if (crashes.isNotEmpty()) {
      val crash = crashes[0]
      this.crashes.removeAt(0)
      throw crash
    }
    val existing = this.responses[0]
    this.responses.removeAt(0)
    return existing
  }
}
