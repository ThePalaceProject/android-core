package org.nypl.simplified.books.time.tracking.api

import java.time.OffsetDateTime

/**
 * An event recording that time tracking has ended for a book.
 *
 * @param time The time at which the event was recorded
 * @param libraryID The identifier of the account provider
 * @param bookID The identifier of the book
 */
data class TimeTrackingStopped(
  override val time: OffsetDateTime,
  override val libraryID: String,
  override val bookID: String,
) : TimeTrackingEvent {
  override val operationName: String
    get() = OPERATION_NAME

  companion object {
    const val OPERATION_NAME: String = "TimeTrackingStopped"
  }
}
