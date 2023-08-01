package org.nypl.simplified.books.time.tracking

import java.net.URI

data class TimeTrackingInfo(
  val libraryId: String,
  val bookId: String,
  val timeTrackingUri: URI?,
  val timeEntries: List<TimeTrackingEntry>
)

