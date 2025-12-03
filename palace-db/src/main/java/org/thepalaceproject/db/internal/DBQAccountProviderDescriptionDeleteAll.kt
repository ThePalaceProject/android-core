package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteAllType

internal object DBQAccountProviderDescriptionDeleteAll : DBQAccountProviderDescriptionDeleteAllType {

  private val queryText = """
    DELETE FROM account_provider_descriptions
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: Unit
  ) {
    transaction.connection.connection.prepareStatement(this.queryText).use { statement ->
      statement.executeUpdate()
    }
  }
}
