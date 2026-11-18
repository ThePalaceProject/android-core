package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType

internal object DBQTimeTrackingSpanDelete : DBQTimeTrackingSpanDeleteType {
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
      DELETE FROM time_tracking_spans
      WHERE tts_span_uuid IN ($placeholders)
      """.trimIndent()

    var total = 0
    val chunkSize = 500
    for (offset in parameters.indices step chunkSize) {
      val chunk = parameters.subList(offset, minOf(offset + chunkSize, parameters.size))
      total +=
        transaction.connection.connection
          .prepareStatement(queryText)
          .use { statement ->
            chunk.forEachIndexed { index, uuid ->
              statement.setString(index + 1, uuid)
            }
            statement.executeUpdate()
          }
    }
    return total
  }
}
