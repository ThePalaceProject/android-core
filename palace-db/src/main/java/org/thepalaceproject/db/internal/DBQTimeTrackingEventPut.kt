package org.thepalaceproject.db.internal

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttempt
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailed
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedExceptionally
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedPermanently
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptSucceeded
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStarted
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStopped
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import java.time.OffsetDateTime

internal object DBQTimeTrackingEventPut : DBQTimeTrackingEventPutType {
  private val queryText =
    """
    INSERT INTO time_tracking_events (
      ttev_time,
      ttev_operation,
      ttev_library_id,
      ttev_book_id,
      ttev_entry_ulid,
      ttev_during_minute,
      ttev_seconds_played,
      ttev_exception
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: TimeTrackingEvent,
  ) {
    val operationName: String
    val entryID: String?
    val duringMinute: OffsetDateTime?
    val secondsPlayed: Int?
    val exception: String?

    when (val event = parameters) {
      is TimeTrackingStarted -> {
        operationName = event.operationName
        entryID = null
        duringMinute = null
        secondsPlayed = null
        exception = null
      }

      is TimeTrackingStopped -> {
        operationName = event.operationName
        entryID = null
        duringMinute = null
        secondsPlayed = null
        exception = null
      }

      is TimeTrackingEntryCreated -> {
        operationName = event.operationName
        entryID = event.entryID
        duringMinute = event.duringMinute
        secondsPlayed = event.secondsPlayed
        exception = null
      }

      is TimeTrackingSendAttempt -> {
        operationName = event.operationName
        entryID = event.entryID
        duringMinute = null
        secondsPlayed = event.secondsPlayed
        exception = null
      }

      is TimeTrackingSendAttemptSucceeded -> {
        operationName = event.operationName
        entryID = event.entryID
        duringMinute = null
        secondsPlayed = event.secondsPlayed
        exception = null
      }

      is TimeTrackingSendAttemptFailed -> {
        operationName = event.operationName
        entryID = event.entryID
        duringMinute = null
        secondsPlayed = event.secondsPlayed
        exception = null
      }

      is TimeTrackingSendAttemptFailedExceptionally -> {
        operationName = event.operationName
        entryID = event.entryID
        duringMinute = null
        secondsPlayed = event.secondsPlayed
        exception = event.exception
      }

      is TimeTrackingSendAttemptFailedPermanently -> {
        operationName = event.operationName
        entryID = event.entryID
        duringMinute = null
        secondsPlayed = event.secondsPlayed
        exception = null
      }
    }

    transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.setString(1, TimeTrackingTime.formatUTC(parameters.time))
        statement.setString(2, operationName)
        statement.setString(3, parameters.libraryID)
        statement.setString(4, parameters.bookID)
        if (entryID != null) {
          statement.setString(5, entryID)
        } else {
          statement.setNull(5, java.sql.Types.VARCHAR)
        }
        if (duringMinute != null) {
          statement.setString(6, TimeTrackingTime.format(duringMinute))
        } else {
          statement.setNull(6, java.sql.Types.VARCHAR)
        }
        if (secondsPlayed != null) {
          statement.setInt(7, secondsPlayed)
        } else {
          statement.setNull(7, java.sql.Types.INTEGER)
        }
        if (exception != null) {
          statement.setString(8, exception)
        } else {
          statement.setNull(8, java.sql.Types.VARCHAR)
        }
        statement.executeUpdate()
      }
  }
}
