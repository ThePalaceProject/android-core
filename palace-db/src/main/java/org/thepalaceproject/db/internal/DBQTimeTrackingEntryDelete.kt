package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType

internal object DBQTimeTrackingEntryDelete : DBQTimeTrackingEntryDeleteType {
  private const val CHUNK_SIZE: Int = 500

  override fun execute(
    transaction: DBTransactionType,
    parameters: List<String>,
  ): Int {
    if (parameters.isEmpty()) {
      return 0
    }

    var total = 0
    for (offset in parameters.indices step this.CHUNK_SIZE) {
      val chunk = parameters.subList(offset, minOf(offset + this.CHUNK_SIZE, parameters.size))
      val placeholders = chunk.joinToString(", ") { "?" }
      val queryText =
        """
        DELETE FROM time_tracking_entries
        WHERE tte_entry_ulid IN ($placeholders)
        """.trimIndent()
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
