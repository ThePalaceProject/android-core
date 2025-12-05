package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteType
import java.net.URI

internal object DBQAccountProviderDescriptionDelete : DBQAccountProviderDescriptionDeleteType {

  private val queryText = """
    DELETE FROM account_provider_descriptions
      WHERE account_provider_descriptions.apd_id = ?
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: Set<URI>
  ) {
    transaction.connection.connection.prepareStatement(this.queryText).use { statement ->
      for (id in parameters) {
        statement.setString(1, id.toString())
        statement.executeUpdate()
      }
    }
  }
}
