package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySetting
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySettingsPutType

internal object DBQAccountRegistrySettingsPut : DBQAccountRegistrySettingsPutType {
  private val queryText =
    """
INSERT INTO account_registry_settings (
  ars_setting_name,
  ars_setting_type,
  ars_setting_value
) VALUES ($1, $2, $3)
  ON CONFLICT DO UPDATE SET
    ars_setting_name  = $1,
    ars_setting_type  = $2,
    ars_setting_value = $3
    """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    parameters: DBQAccountRegistrySetting
  ) {
    transaction.connection.connection.prepareStatement(this.queryText).use { statement ->
      statement.setString(1, parameters.name)
      statement.setString(2, parameters.javaClass.simpleName)
      statement.setString(3,
        when (parameters) {
          is DBQAccountRegistrySetting.TimeSetting -> {
            parameters.value.toString()
          }
        }
      )
      statement.execute()
    }
  }
}
