package org.thepalaceproject.db.api.queries

import org.nypl.simplified.accounts.api.AccountProvider
import org.thepalaceproject.db.api.DBQueryType
import java.net.URI

interface DBQAccountProviderGetType : DBQueryType<URI, AccountProvider?>
