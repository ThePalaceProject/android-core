package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQSchemaVersionType

internal object DBQSchemaVersion : DBQSchemaVersionType {

  private val text = """
    SELECT schema_version.version_number
      FROM schema_version
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: Unit
  ): Long {
    return transaction.connection.connection.prepareStatement(text).use { statement ->
      statement.executeQuery().use { resultSet ->
        if (resultSet.next()) {
          resultSet.getLong(1)
        } else {
          throw IllegalStateException("Version table is empty!")
        }
      }
    }
  }
}
