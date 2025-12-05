package org.thepalaceproject.db.internal

import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionListType
import java.sql.ResultSet

internal object DBQAccountProviderDescriptionList : DBQAccountProviderDescriptionListType {

  private val logger =
    LoggerFactory.getLogger(DBQAccountProviderDescriptionList::class.java)

  private val queryTextWithStart = """
    SELECT
      apd.apd_id,
      apd.apd_updated_time_last,
      apd.apd_data_format,
      apd.apd_data
    FROM account_provider_descriptions AS apd
    WHERE apd.apd_id > ?
    ORDER BY apd.apd_id
    LIMIT ?
  """.trimIndent()

  private val queryTextWithoutStart = """
    SELECT
      apd.apd_id,
      apd.apd_updated_time_last,
      apd.apd_data_format,
      apd.apd_data
    FROM account_provider_descriptions AS apd
    ORDER BY apd.apd_id
    LIMIT ?
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: DBQAccountProviderDescriptionListType.Parameters
  ): List<AccountProviderDescription> {
    val startingId = parameters.startingId
    return if (startingId != null) {
      transaction.connection.connection.prepareStatement(this.queryTextWithStart)
        .use { statement ->
          statement.setString(1, startingId.toString())
          statement.setInt(2, parameters.limit)
          statement.executeQuery().use { resultSet ->
            this.parseResults(transaction, resultSet)
          }
        }
    } else {
      transaction.connection.connection.prepareStatement(this.queryTextWithoutStart)
        .use { statement ->
          statement.setInt(1, parameters.limit)
          statement.executeQuery().use { resultSet ->
            this.parseResults(transaction, resultSet)
          }
        }
    }
  }

  private fun parseResults(
    transaction: DBTransactionType,
    resultSet: ResultSet
  ): List<AccountProviderDescription> {
    val data = mutableListOf<AccountProviderDescription>()
    val timeThen = System.nanoTime()
    while (resultSet.next()) {
      data.add(DBAccountProviderDescriptions.parseFromResult(transaction, resultSet))
    }
    val timeNow = System.nanoTime()
    val timeDiff = (timeNow - timeThen).toDouble() / 1_000_000
    this.logger.debug("Loaded provider descriptions in {} ms", timeDiff)
    return data.toList()
  }
}
