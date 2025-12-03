package org.thepalaceproject.db.api.queries

import org.nypl.simplified.accounts.api.AccountProvider
import org.thepalaceproject.db.api.DBQueryType
import java.net.URI

interface DBQAccountProviderListType : DBQueryType<DBQAccountProviderListType.Parameters, List<AccountProvider>> {
  data class Parameters(
    val startingId: URI?,
    val limit: Int
  )
}
