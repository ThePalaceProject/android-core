package org.thepalaceproject.db.internal

import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.json.AccountProvidersJSON
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderPutType
import java.time.ZoneOffset

internal object DBQAccountProviderPut : DBQAccountProviderPutType {

  private val text = """
    INSERT INTO account_providers (
      ap_id,
      ap_updated_time_last,
      ap_data_format,
      ap_data
    ) VALUES (
      ?,
      ?,
      ?,
      ?
    ) ON CONFLICT DO UPDATE SET
      ap_updated_time_last = ?,
      ap_data_format       = ?,
      ap_data              = ?
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    baseDescription: AccountProvider
  ) {
    val timestamp =
      baseDescription.updated.withOffsetSameInstant(ZoneOffset.UTC)
        .toString()

    val description =
      DBAccountProviders.forceUTC(baseDescription)

    val serializedData =
      AccountProvidersJSON.serializeToBytes(description)

    return transaction.connection.connection.prepareStatement(this.text).use { st ->
      st.setString(1, description.id.toString())

      st.setString(2, timestamp)
      st.setString(3, DBQAccountProviderGet.FORMAT_ACCOUNT_PROVIDER_JSON)
      st.setBytes(4, serializedData)

      st.setString(5, timestamp)
      st.setString(6, DBQAccountProviderGet.FORMAT_ACCOUNT_PROVIDER_JSON)
      st.setBytes(7, serializedData)

      st.execute()
    }
  }
}
