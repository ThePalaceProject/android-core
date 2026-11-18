package org.thepalaceproject.db.internal

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEventOperation
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
            data.add(parseResult(resultSet))
          }
        }
      }
    return data.toList()
  }

  private fun parseResult(resultSet: ResultSet): TimeTrackingEvent {
    val duringMinuteText = resultSet.getString(7)
    val secondsPlayed: Int?
    val seconds = resultSet.getInt(8)
    secondsPlayed =
      if (resultSet.wasNull()) {
        null
      } else {
        seconds
      }

    return TimeTrackingEvent(
      operation = TimeTrackingEventOperation.valueOf(resultSet.getString(3)),
      time = TimeTrackingTime.parse(resultSet.getString(2)),
      libraryID = resultSet.getString(4),
      bookID = resultSet.getString(5),
      entryID = resultSet.getString(6),
      duringMinute =
        if (duringMinuteText == null) {
          null
        } else {
          TimeTrackingTime.parse(duringMinuteText)
        },
      secondsPlayed = secondsPlayed,
      exception = resultSet.getString(9),
    )
  }
}
