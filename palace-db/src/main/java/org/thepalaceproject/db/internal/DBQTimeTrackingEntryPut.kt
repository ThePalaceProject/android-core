package org.thepalaceproject.db.internal

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType

internal object DBQTimeTrackingEntryPut : DBQTimeTrackingEntryPutType {
  private val queryText =
    """
    INSERT OR IGNORE INTO time_tracking_entries (
      tte_entry_ulid,
      tte_account_uuid,
      tte_library_id,
      tte_book_id,
      tte_target_uri,
      tte_during_minute,
      tte_seconds_played
    )
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: TimeTrackingEntryOutgoing,
  ): Int {
    return transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.setString(1, parameters.timeEntry.id)
        statement.setString(2, parameters.accountID.toString())
        statement.setString(3, parameters.libraryID.toString())
        statement.setString(4, parameters.bookID.toString())
        statement.setString(5, parameters.targetURI.toString())
        statement.setString(6, TimeTrackingTime.format(parameters.timeEntry.duringMinute))
        statement.setInt(7, parameters.timeEntry.secondsPlayed)
        statement.executeUpdate()
      }
  }
}
