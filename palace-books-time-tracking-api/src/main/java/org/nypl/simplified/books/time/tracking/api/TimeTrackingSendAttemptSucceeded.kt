package org.nypl.simplified.books.time.tracking.api

import java.time.OffsetDateTime

/**
 * An event recording that an attempt to send a time tracking entry has succeeded; the entry was
 * acknowledged as delivered by the server.
 *
 * @param time The time at which the event was recorded
 * @param libraryID The identifier of the account provider
 * @param bookID The identifier of the book
 * @param entryID The identifier of the entry that was sent
 * @param secondsPlayed The number of seconds of playback in the entry
 */
data class TimeTrackingSendAttemptSucceeded(
  override val time: OffsetDateTime,
  override val libraryID: String,
  override val bookID: String,
  val entryID: String,
  val secondsPlayed: Int,
) : TimeTrackingEvent {
  override val operationName: String
    get() = OPERATION_NAME

  companion object {
    const val OPERATION_NAME: String = "TimeTrackingSendAttemptSucceeded"
  }
}
