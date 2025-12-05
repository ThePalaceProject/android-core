package org.thepalaceproject.db.internal

import org.nypl.simplified.accounts.api.AccountProvider
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderListType
import java.sql.ResultSet

internal object DBQAccountProviderList : DBQAccountProviderListType {

  private val queryTextWithStart = """
    SELECT
      ap.ap_id,
      ap.ap_updated_time_last,
      ap.ap_data_format,
      ap.ap_data
    FROM account_providers AS ap
    WHERE ap.ap_id > ?
    ORDER BY ap.ap_id
    LIMIT ?
  """.trimIndent()

  private val queryTextWithoutStart = """
    SELECT
      ap.ap_id,
      ap.ap_updated_time_last,
      ap.ap_data_format,
      ap.ap_data
    FROM account_providers AS ap
    ORDER BY ap.ap_id
    LIMIT ?
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: DBQAccountProviderListType.Parameters
  ): List<AccountProvider> {
    val startingId = parameters.startingId
    return if (startingId != null) {
      transaction.connection.connection.prepareStatement(queryTextWithStart)
        .use { statement ->
          statement.setString(1, startingId.toString())
          statement.setInt(2, parameters.limit)
          statement.executeQuery().use { resultSet ->
            parseResults(transaction, resultSet)
          }
        }
    } else {
      transaction.connection.connection.prepareStatement(queryTextWithoutStart)
        .use { statement ->
          statement.setInt(1, parameters.limit)
          statement.executeQuery().use { resultSet ->
            parseResults(transaction, resultSet)
          }
        }
    }
  }

  private fun parseResults(
    transaction: DBTransactionType,
    resultSet: ResultSet
  ): List<AccountProvider> {
    val data = mutableListOf<AccountProvider>()
    while (resultSet.next()) {
      data.add(DBAccountProviders.parseFromResult(transaction, resultSet))
    }
    return data.toList()
  }
}
