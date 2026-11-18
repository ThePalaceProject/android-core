package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType

internal object DBQTimeTrackingEntryDelete : DBQTimeTrackingEntryDeleteType {
  override fun execute(
    transaction: DBTransactionType,
    parameters: List<String>,
  ): Int {
    if (parameters.isEmpty()) {
      return 0
    }

    val placeholders = parameters.joinToString(", ") { "?" }
    val queryText =
      """
      DELETE FROM time_tracking_entries
      WHERE tte_entry_ulid IN ($placeholders)
      """.trimIndent()

    var total = 0
    val chunkSize = 500
    for (offset in parameters.indices step chunkSize) {
      val chunk = parameters.subList(offset, minOf(offset + chunkSize, parameters.size))
      total +=
        transaction.connection.connection
          .prepareStatement(queryText)
          .use { statement ->
            chunk.forEachIndexed { index, ulid ->
              statement.setString(index + 1, ulid)
            }
            statement.executeUpdate()
          }
    }
    return total
  }
}
