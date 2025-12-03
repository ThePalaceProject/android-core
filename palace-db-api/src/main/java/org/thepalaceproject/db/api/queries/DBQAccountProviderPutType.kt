package org.thepalaceproject.db.api.queries

import org.nypl.simplified.accounts.api.AccountProvider
import org.thepalaceproject.db.api.DBQueryType

interface DBQAccountProviderPutType : DBQueryType<AccountProvider, Unit>
