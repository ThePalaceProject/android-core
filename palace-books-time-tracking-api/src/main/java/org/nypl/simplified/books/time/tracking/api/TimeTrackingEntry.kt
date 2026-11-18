package org.nypl.simplified.books.time.tracking.api

import java.time.OffsetDateTime

/**
 * A single time tracking entry; a number of seconds of playback attributed to a specific
 * minute.
 *
 * @param id The unique identifier of the entry
 * @param duringMinute The minute to which the seconds of playback are attributed
 * @param secondsPlayed The number of seconds of playback, in the range [0, 60]
 */
data class TimeTrackingEntry(
  val id: String,
  val duringMinute: OffsetDateTime,
  val secondsPlayed: Int,
)
