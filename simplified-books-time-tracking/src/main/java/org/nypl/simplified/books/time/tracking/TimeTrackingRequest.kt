package org.nypl.simplified.books.time.tracking

import java.net.URI

data class TimeTrackingRequest(
  val bookId: String,
  val libraryId: URI,
  val timeTrackingUri: URI,
  val timeEntries: List<TimeTrackingEntry>
)
