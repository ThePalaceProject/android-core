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
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import java.sql.ResultSet
import java.time.OffsetDateTime

internal object DBQTimeTrackingEventSelect : DBQTimeTrackingEventSelectType {
  private val queryText =
    """
    SELECT
      ttev.ttev_sequence,
      ttev.ttev_time,
      ttev.ttev_operation,
      ttev.ttev_library_id,
      ttev.ttev_book_id,
      ttev.ttev_entry_ulid,
      ttev.ttev_during_minute,
      ttev.ttev_seconds_played,
      ttev.ttev_exception
    FROM time_tracking_events AS ttev
    WHERE ttev.ttev_time >= ?
    ORDER BY ttev.ttev_sequence
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: OffsetDateTime,
  ): List<TimeTrackingEvent> {
    val data = mutableListOf<TimeTrackingEvent>()
    transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.setString(1, TimeTrackingTime.formatUTC(parameters))
        statement.executeQuery().use { resultSet ->
          while (resultSet.next()) {
            data.add(this.parseResult(resultSet))
          }
        }
      }
    return data.toList()
  }

  private fun parseResult(resultSet: ResultSet): TimeTrackingEvent {
    val time =
      TimeTrackingTime.parse(resultSet.getString(2))
    val operationName =
      resultSet.getString(3)
    val libraryID =
      resultSet.getString(4)
    val bookID =
      resultSet.getString(5)
    val entryID =
      resultSet.getString(6)
    val duringMinuteText =
      resultSet.getString(7)
    val secondsPlayed: Int? =
      if (resultSet.getString(8) == null) {
        null
      } else {
        resultSet.getInt(8)
      }
    val exception =
      resultSet.getString(9)

    return when (operationName) {
      TimeTrackingStarted.OPERATION_NAME -> {
        TimeTrackingStarted(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
        )
      }

      TimeTrackingStopped.OPERATION_NAME -> {
        TimeTrackingStopped(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
        )
      }

      TimeTrackingEntryCreated.OPERATION_NAME -> {
        val duringMinute =
          requireNotNull(duringMinuteText) {
            "An entry created event is missing its during minute."
          }
        TimeTrackingEntryCreated(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
          entryID =
            requireNotNull(entryID) {
              "An entry created event is missing its entry id."
            },
          duringMinute =
            TimeTrackingTime.parse(duringMinute),
          secondsPlayed =
            requireNotNull(secondsPlayed) {
              "An entry created event is missing its seconds played."
            },
        )
      }

      TimeTrackingSendAttempt.OPERATION_NAME -> {
        TimeTrackingSendAttempt(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
          entryID =
            requireNotNull(entryID) {
              "A send attempt event is missing its entry id."
            },
          secondsPlayed =
            requireNotNull(secondsPlayed) {
              "A send attempt event is missing its seconds played."
            },
        )
      }

      TimeTrackingSendAttemptSucceeded.OPERATION_NAME -> {
        TimeTrackingSendAttemptSucceeded(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
          entryID =
            requireNotNull(entryID) {
              "A send attempt event is missing its entry id."
            },
          secondsPlayed =
            requireNotNull(secondsPlayed) {
              "A send attempt event is missing its seconds played."
            },
        )
      }

      TimeTrackingSendAttemptFailed.OPERATION_NAME -> {
        TimeTrackingSendAttemptFailed(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
          entryID =
            requireNotNull(entryID) {
              "A send attempt event is missing its entry id."
            },
          secondsPlayed =
            requireNotNull(secondsPlayed) {
              "A send attempt event is missing its seconds played."
            },
        )
      }

      TimeTrackingSendAttemptFailedExceptionally.OPERATION_NAME -> {
        TimeTrackingSendAttemptFailedExceptionally(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
          entryID =
            requireNotNull(entryID) {
              "A send attempt event is missing its entry id."
            },
          secondsPlayed =
            requireNotNull(secondsPlayed) {
              "A send attempt event is missing its seconds played."
            },
          exception =
            requireNotNull(exception) {
              "A send attempt failed exceptionally event is missing its exception."
            },
        )
      }

      TimeTrackingSendAttemptFailedPermanently.OPERATION_NAME -> {
        TimeTrackingSendAttemptFailedPermanently(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
          entryID =
            requireNotNull(entryID) {
              "A send attempt event is missing its entry id."
            },
          secondsPlayed =
            requireNotNull(secondsPlayed) {
              "A send attempt event is missing its seconds played."
            },
        )
      }

      else -> {
        throw IllegalArgumentException("Unknown operation '$operationName'")
      }
    }
  }
}
