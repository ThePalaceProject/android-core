package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType

internal object DBQTimeTrackingEntryDelete : DBQTimeTrackingEntryDeleteType {
  private val queryText =
    """
DELETE FROM time_tracking_entries
WHERE tte_entry_ulid = $1
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: List<String>,
  ): Int {
    if (parameters.isEmpty()) {
      return 0
    }

    var total = 0
    transaction.connection.connection.prepareStatement(this.queryText).use { statement ->
      for (ulid in parameters) {
        statement.setString(1, ulid)
        total += statement.executeUpdate()
      }
    }
    return total
  }
}
