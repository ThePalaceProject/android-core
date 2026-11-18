package org.nypl.simplified.books.time.tracking.api

import java.time.OffsetDateTime

/**
 * A single event in the time tracking event log.
 *
 * The event log is an append-only audit trail of the operations performed by the time tracking
 * pipeline. Fields that do not apply to a given [operation] are null.
 *
 * @param operation The operation that was performed
 * @param time The time at which the operation was performed
 * @param libraryID The identifier of the account provider
 * @param bookID The identifier of the book
 * @param entryID The identifier of the time tracking entry, if any
 * @param duringMinute The minute to which an entry applies, if any
 * @param secondsPlayed The number of seconds played, if known
 * @param exception The text of an unexpected exception, if any
 */
data class TimeTrackingEvent(
  val operation: TimeTrackingEventOperation,
  val time: OffsetDateTime,
  val libraryID: String,
  val bookID: String,
  val entryID: String?,
  val duringMinute: OffsetDateTime?,
  val secondsPlayed: Int?,
  val exception: String?,
)
