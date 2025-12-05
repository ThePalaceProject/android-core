package org.thepalaceproject.db.internal

import org.nypl.simplified.accounts.api.AccountProvider
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderGetType
import java.net.URI

internal object DBQAccountProviderGet : DBQAccountProviderGetType {

  const val FORMAT_ACCOUNT_PROVIDER_JSON =
    "org.nypl.simplified.accounts.json.AccountProvidersJSON"

  private val text = """
    SELECT
      ap.ap_id,
      ap.ap_updated_time_last,
      ap.ap_data_format,
      ap.ap_data
    FROM account_providers AS ap
      WHERE ap.ap_id = ?
        LIMIT 1
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: URI
  ): AccountProvider? {
    return transaction.connection.connection.prepareStatement(this.text).use { statement ->
      statement.setString(1, parameters.toString())
      statement.executeQuery().use { resultSet ->
        if (resultSet.next()) {
          DBAccountProviders.parseFromResult(transaction, resultSet)
        } else {
          null
        }
      }
    }
  }
}
