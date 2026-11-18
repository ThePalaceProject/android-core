package org.thepalaceproject.db.internal

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import java.net.URI
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

internal object DBQTimeTrackingSpanSelect : DBQTimeTrackingSpanSelectType {
  private val queryText =
    """
    SELECT
      tts.tts_span_uuid,
      tts.tts_account_uuid,
      tts.tts_library_id,
      tts.tts_book_id,
      tts.tts_time_started,
      tts.tts_time_ended,
      tts.tts_time_received,
      tts.tts_target_uri
    FROM time_tracking_spans AS tts
    WHERE tts.tts_time_received < ?
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: OffsetDateTime,
  ): List<TimeTrackingReceivedSpan> {
    val data = mutableListOf<TimeTrackingReceivedSpan>()
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

  private fun parseResult(resultSet: ResultSet): TimeTrackingReceivedSpan {
    return TimeTrackingReceivedSpan(
      id = UUID.fromString(resultSet.getString(1)),
      accountID = AccountID(UUID.fromString(resultSet.getString(2))),
      libraryID = URI.create(resultSet.getString(3)),
      bookID = PlayerPalaceID(resultSet.getString(4)),
      timeStarted = TimeTrackingTime.parse(resultSet.getString(5)),
      timeEnded = TimeTrackingTime.parse(resultSet.getString(6)),
      timeReceived = TimeTrackingTime.parse(resultSet.getString(7)),
      targetURI = URI.create(resultSet.getString(8)),
    )
  }
}
