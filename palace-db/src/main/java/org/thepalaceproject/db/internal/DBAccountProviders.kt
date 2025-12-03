package org.thepalaceproject.db.internal

import org.joda.time.DateTimeZone
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.json.AccountProvidersJSON
import org.thepalaceproject.db.api.DBException
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.internal.DBQAccountProviderGet.FORMAT_ACCOUNT_PROVIDER_JSON
import java.sql.ResultSet

internal object DBAccountProviders {

  fun forceUTC(description: AccountProvider): AccountProvider {
    return description.copy(
      updated = description.updated.withZone(DateTimeZone.UTC)
    )
  }

  fun parseFromResult(
    transaction: DBTransactionType,
    resultSet: ResultSet
  ): AccountProvider {
    return when (val result = resultSet.getString("ap_data_format")) {
      FORMAT_ACCOUNT_PROVIDER_JSON -> {
        this.parseFromAccountProviderJSON(resultSet)
      }

      else -> {
        throw DBException("Unsupported format: $result", Exception())
      }
    }
  }

  private fun parseFromAccountProviderJSON(
    resultSet: ResultSet
  ): AccountProvider {
    return resultSet.getBinaryStream("ap_data").use { data ->
      AccountProvidersJSON.deserializeOneFromStream(data)
    }
  }
}
