package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySetting
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySettingsGetType
import java.io.IOException
import java.sql.ResultSet
import java.time.OffsetDateTime

internal object DBQAccountRegistrySettingsGet : DBQAccountRegistrySettingsGetType {

  private val queryText = """
SELECT
  ars_setting_name,
  ars_setting_type,
  ars_setting_value
FROM
  account_registry_settings
WHERE ars_setting_name = $1
LIMIT 1
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: String,
  ): DBQAccountRegistrySetting? {
    return transaction.connection.connection.prepareStatement(this.queryText).use { statement ->
      statement.setString(1, parameters)
      statement.executeQuery().use { results ->
        while (results.next()) {
          return readResult(parameters, results)
        }
        null
      }
    }
  }

  fun readResult(
    name: String,
    results: ResultSet
  ): DBQAccountRegistrySetting? {
    return when (val type = results.getString("ars_setting_type")) {
      "TimeSetting" -> {
        DBQAccountRegistrySetting.TimeSetting(
          results.getString("ars_setting_name"),
          OffsetDateTime.parse(results.getString("ars_setting_value"))
        )
      }

      else -> {
        throw IOException("Unrecognized type '$type' for setting '$name'")
      }
    }
  }
}
