package org.nypl.simplified.books.time.tracking

import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import java.time.OffsetDateTime

/**
 * The construction of time tracking data for inclusion in diagnostic reports.
 *
 * The data is rendered as CSV; one file per table. This replaces the old practice of
 * including the raw time tracking directory in reports.
 */
object TimeTrackingReport {
  /**
   * Build the CSV representation of the pending spans.
   *
   * @param database The database
   * @param now The current time, used as the receive-time cutoff (spans received after this
   *   time cannot yet exist)
   *
   * @return The CSV text
   */
  fun spansCsv(
    database: DBType,
    now: OffsetDateTime,
  ): String {
    val spans =
      database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(DBQTimeTrackingSpanSelectType::class.java, now)
        }
      }

    val lines =
      mutableListOf(
        "span_id,account_id,library_id,book_id,time_started,time_ended,time_received,target_uri",
      )
    for (span in spans) {
      lines.add(
        listOf(
          span.id.toString(),
          span.accountID.toString(),
          span.libraryID.toString(),
          span.bookID.value,
          TimeTrackingTime.format(span.timeStarted),
          TimeTrackingTime.format(span.timeEnded),
          TimeTrackingTime.format(span.timeReceived),
          span.targetURI.toString(),
        ).joinToString(","),
      )
    }
    return lines.joinToString("\n") + "\n"
  }

  /**
   * Build the CSV representation of the pending entries.
   *
   * @param database The database
   *
   * @return The CSV text
   */
  fun entriesCsv(database: DBType): String {
    val entries =
      database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
        }
      }

    val lines =
      mutableListOf(
        "entry_id,account_id,library_id,book_id,target_uri,during_minute,seconds_played",
      )
    for (entry in entries) {
      lines.add(
        listOf(
          entry.timeEntry.id,
          entry.accountID.toString(),
          entry.libraryID.toString(),
          entry.bookID.value,
          entry.targetURI.toString(),
          TimeTrackingTime.format(entry.timeEntry.duringMinute),
          entry.timeEntry.secondsPlayed.toString(),
        ).joinToString(","),
      )
    }
    return lines.joinToString("\n") + "\n"
  }

  /**
   * Build the CSV representation of the event log.
   *
   * @param database The database
   * @param since The lower time bound of the events to include
   *
   * @return The CSV text
   */
  fun eventsCsv(
    database: DBType,
    since: OffsetDateTime,
  ): String {
    val events =
      database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(DBQTimeTrackingEventSelectType::class.java, since)
        }
      }

    val lines =
      mutableListOf(
        "time,operation,library_id,book_id,entry_id,during_minute,seconds_played,exception",
      )
    for (event in events) {
      val duringMinute = event.duringMinute
      val secondsPlayed = event.secondsPlayed
      val duringMinuteText =
        if (duringMinute != null) {
          TimeTrackingTime.format(duringMinute)
        } else {
          ""
        }
      val secondsPlayedText =
        if (secondsPlayed != null) {
          secondsPlayed.toString()
        } else {
          ""
        }
      lines.add(
        listOf(
          TimeTrackingTime.format(event.time),
          event.operation.name,
          event.libraryID,
          event.bookID,
          csvEscape(event.entryID),
          duringMinuteText,
          secondsPlayedText,
          csvEscape(event.exception),
        ).joinToString(","),
      )
    }
    return lines.joinToString("\n") + "\n"
  }

  private fun csvEscape(field: String?): String {
    if (field == null) {
      return ""
    }
    val needsQuoting =
      field.contains(",") ||
        field.contains("\"") ||
        field.contains("\n") ||
        field.contains("\r")
    if (!needsQuoting) {
      return field
    }
    return "\"" + field.replace("\"", "\"\"") + "\""
  }
}
