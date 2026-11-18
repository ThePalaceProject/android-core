package org.thepalaceproject.db.internal

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType

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
    transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.setString(1, TimeTrackingTime.formatUTC(parameters.time))
        statement.setString(2, parameters.operation.name)
        statement.setString(3, parameters.libraryID)
        statement.setString(4, parameters.bookID)
        statement.setString(5, parameters.entryID)
        val duringMinute = parameters.duringMinute
        if (duringMinute != null) {
          statement.setString(6, TimeTrackingTime.format(duringMinute))
        } else {
          statement.setNull(6, java.sql.Types.VARCHAR)
        }
        val secondsPlayed = parameters.secondsPlayed
        if (secondsPlayed != null) {
          statement.setInt(7, secondsPlayed)
        } else {
          statement.setNull(7, java.sql.Types.INTEGER)
        }
        statement.setString(8, parameters.exception)
        statement.executeUpdate()
      }
  }
}
