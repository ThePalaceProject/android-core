package org.nypl.simplified.books.time.tracking

data class TimeTrackingEntry(
  val id: String,
  val duringMinute: String,
  val secondsPlayed: Int
)
