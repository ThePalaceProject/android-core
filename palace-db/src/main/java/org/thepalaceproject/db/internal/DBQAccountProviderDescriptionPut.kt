package org.thepalaceproject.db.internal

import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollection
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionSerializersType
import org.nypl.simplified.links.Link
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType
import java.io.ByteArrayOutputStream
import java.net.URI

object DBQAccountProviderDescriptionPut : DBQAccountProviderDescriptionPutType {

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
    baseDescription: AccountProviderDescription
  ) {
    val timestamp =
      baseDescription.updated.withZone(DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime())

    val description =
      DBAccountProviderDescriptions.forceUTC(baseDescription)

    val document =
      AccountProviderDescriptionCollection(
        providers = listOf(description),
        links = listOf(Link.LinkBasic(href = URI.create("urn:self"), relation = "self")),
        metadata = AccountProviderDescriptionCollection.Metadata(title = "")
      )

    val outputStream =
      ByteArrayOutputStream()

    transaction.service(AccountProviderDescriptionCollectionSerializersType::class.java)
      .createSerializer(URI.create("urn:any"), outputStream, document)
      .serialize()

    val serializedData =
      outputStream.toByteArray()

    return transaction.connection.connection.prepareStatement(this.text).use { st ->
      st.setString(1, description.id.toString())

      st.setString(2, timestamp)
      st.setBoolean(3, description.isProduction)
      st.setString(4, DBQAccountProviderDescriptionGet.FORMAT_OPDS2_COLLECTION)
      st.setBytes(5, serializedData)

      st.setString(6, timestamp)
      st.setBoolean(7, description.isProduction)
      st.setString(8, DBQAccountProviderDescriptionGet.FORMAT_OPDS2_COLLECTION)
      st.setBytes(9, serializedData)

      st.execute()
    }
  }
}
