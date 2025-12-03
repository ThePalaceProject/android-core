package org.thepalaceproject.db.internal

import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionGetType
import java.net.URI

internal object DBQAccountProviderDescriptionGet : DBQAccountProviderDescriptionGetType {

  const val FORMAT_OPDS2_COLLECTION =
    "org.thepalaceproject.opds2.account_provider_description_collection"

  private val text = """
    SELECT
      apd.apd_id,
      apd.apd_updated_time_last,
      apd.apd_production,
      apd.apd_data_format,
      apd.apd_data
    FROM account_provider_descriptions AS apd
      WHERE apd.apd_id = ?
        LIMIT 1
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: URI
  ): AccountProviderDescription? {
    return transaction.connection.connection.prepareStatement(this.text).use { statement ->
      statement.setString(1, parameters.toString())
      statement.executeQuery().use { resultSet ->
        if (resultSet.next()) {
          DBAccountProviderDescriptions.parseFromResult(transaction, resultSet)
        } else {
          null
        }
      }
    }
  }
}
