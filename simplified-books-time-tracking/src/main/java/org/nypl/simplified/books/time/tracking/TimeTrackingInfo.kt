package org.nypl.simplified.books.time.tracking

import java.net.URI

data class TimeTrackingInfo(
  val accountId: String,
  val bookId: String,
  val libraryId: String,
  val timeTrackingUri: URI,
  val timeEntries: List<TimeTrackingEntry>
)
