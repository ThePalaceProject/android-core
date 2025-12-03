package org.thepalaceproject.db.internal

import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType

internal object DBQAccountProviderDescriptionPut : DBQAccountProviderDescriptionPutType {

  private val text = """
    INSERT INTO account_provider_descriptions (
      apd_id,
      apd_updated_time_last,
      apd_production,
      apd_data_format,
      apd_data
    ) VALUES (
      ?,
      ?,
      ?,
      ?,
      ?
    ) ON CONFLICT DO UPDATE SET
      apd_updated_time_last = ?,
      apd_production        = ?,
      apd_data_format       = ?,
      apd_data              = ?
  """.trimIndent()

  override fun execute(
    transaction: DBTransactionType,
    baseDescriptions: List<AccountProviderDescription>
  ) {
    val descriptions =
      baseDescriptions.map { baseDescription ->
        val description =
          DBAccountProviderDescriptions.forceUTC(baseDescription)
        val data: ByteArray =
          DBAccountProviderDescriptionsProtobuf.descriptionToP1Bytes(baseDescription)

        Pair(description, data)
      }

    return transaction.connection.connection.prepareStatement(this.text).use { st ->
      for (d in descriptions) {
        val description = d.first
        val data = d.second

        st.setString(1, description.id.toString())

        val timestamp = description.updated.toString()
        st.setString(2, timestamp)
        st.setBoolean(3, description.isProduction)
        st.setString(4, "DBSerializationProto1")
        st.setBytes(5, data)

        st.setString(6, timestamp)
        st.setBoolean(7, description.isProduction)
        st.setString(8, "DBSerializationProto1")
        st.setBytes(9, data)

        st.addBatch()
      }

      st.executeBatch()
    }
  }
}
