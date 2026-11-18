package org.thepalaceproject.db.internal

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import java.net.URI
import java.sql.ResultSet
import java.util.UUID

internal object DBQTimeTrackingEntrySelect : DBQTimeTrackingEntrySelectType {
  private val queryText =
    """
    SELECT
      tte.tte_entry_ulid,
      tte.tte_account_uuid,
      tte.tte_library_id,
      tte.tte_book_id,
      tte.tte_target_uri,
      tte.tte_during_minute,
      tte.tte_seconds_played
    FROM time_tracking_entries AS tte
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: Unit,
  ): List<TimeTrackingEntryOutgoing> {
    val data = mutableListOf<TimeTrackingEntryOutgoing>()
    transaction.connection.connection
      .prepareStatement(queryText)
      .use { statement ->
        statement.executeQuery().use { resultSet ->
          while (resultSet.next()) {
            data.add(parseResult(resultSet))
          }
        }
      }
    return data.toList()
  }

  private fun parseResult(resultSet: ResultSet): TimeTrackingEntryOutgoing {
    return TimeTrackingEntryOutgoing(
      accountID = AccountID(UUID.fromString(resultSet.getString(2))),
      libraryID = URI.create(resultSet.getString(3)),
      bookID = PlayerPalaceID(resultSet.getString(4)),
      targetURI = URI.create(resultSet.getString(5)),
      timeEntry =
        TimeTrackingEntry(
          id = resultSet.getString(1),
          duringMinute = TimeTrackingTime.parse(resultSet.getString(6)),
          secondsPlayed = resultSet.getInt(7),
        ),
    )
  }
}
