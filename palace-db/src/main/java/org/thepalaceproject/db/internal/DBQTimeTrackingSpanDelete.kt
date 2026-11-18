package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType

internal object DBQTimeTrackingSpanDelete : DBQTimeTrackingSpanDeleteType {
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
        DELETE FROM time_tracking_spans
        WHERE tts_span_uuid IN ($placeholders)
        """.trimIndent()
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
