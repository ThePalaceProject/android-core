package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionIDSetType
import java.net.URI
import java.sql.ResultSet

internal object DBQAccountProviderDescriptionIDSet : DBQAccountProviderDescriptionIDSetType {

  private val queryText = """
    SELECT
      apd.apd_id
    FROM account_provider_descriptions AS apd
    ORDER BY apd.apd_id
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: Unit
  ): Set<URI> {
    return transaction.connection.connection.prepareStatement(queryText)
      .use { statement ->
        statement.executeQuery().use { resultSet ->
          parseResults(transaction, resultSet)
        }
      }
  }

  private fun parseResults(
    transaction: DBTransactionType,
    resultSet: ResultSet
  ): Set<URI> {
    val data = mutableSetOf<URI>()
    while (resultSet.next()) {
      data.add(URI.create(resultSet.getString(1)))
    }
    return data
  }
}
