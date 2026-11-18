package org.thepalaceproject.db.internal

import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPurgeType
import java.time.OffsetDateTime

internal object DBQTimeTrackingEventPurge : DBQTimeTrackingEventPurgeType {
  private val queryText =
    """
    DELETE FROM time_tracking_events
    WHERE ttev_time < ?
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: OffsetDateTime,
  ): Int {
    return transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.setString(1, TimeTrackingTime.formatUTC(parameters))
        statement.executeUpdate()
      }
  }
}
