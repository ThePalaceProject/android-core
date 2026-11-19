package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType

internal object DBQTimeTrackingSpanDelete : DBQTimeTrackingSpanDeleteType {
  private val queryText =
    """
DELETE FROM time_tracking_spans
WHERE tts_span_uuid = $1
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
      for (uuid in parameters) {
        statement.setString(1, uuid)
        total += statement.executeUpdate()
      }
    }
    return total
  }
}
