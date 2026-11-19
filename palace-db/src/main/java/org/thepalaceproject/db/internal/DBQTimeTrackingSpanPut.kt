package org.thepalaceproject.db.internal

import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType

internal object DBQTimeTrackingSpanPut : DBQTimeTrackingSpanPutType {
  private val queryText =
    """
    INSERT OR IGNORE INTO time_tracking_spans (
      tts_span_uuid,
      tts_account_uuid,
      tts_library_id,
      tts_book_id,
      tts_time_started,
      tts_time_ended,
      tts_time_received,
      tts_target_uri
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: TimeTrackingReceivedSpan,
  ): Int {
    return transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.setString(1, parameters.id.toString())
        statement.setString(2, parameters.accountID.toString())
        statement.setString(3, parameters.libraryID.toString())
        statement.setString(4, parameters.bookID.toString())
        statement.setString(5, TimeTrackingTime.format(parameters.timeStarted))
        statement.setString(6, TimeTrackingTime.format(parameters.timeEnded))
        statement.setString(7, TimeTrackingTime.formatUTC(parameters.timeReceived))
        statement.setString(8, parameters.targetURI.toString())
        statement.executeUpdate()
      }
  }
}
