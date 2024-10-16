package org.nypl.simplified.books.time.tracking

data class TimeTrackingServerResponse(
  val responses: List<TimeTrackingServerResponseEntry>,
  val summary: TimeTrackingServerResponseSummary
)
