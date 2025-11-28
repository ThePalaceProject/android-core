package org.nypl.simplified.books.time.tracking

data class TimeTrackingServerResponseSummary(
  val failures: Int,
  val successes: Int,
  val total: Int
)
