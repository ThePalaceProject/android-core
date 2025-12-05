package org.thepalaceproject.db.api.queries

import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.thepalaceproject.db.api.DBQueryType
import java.net.URI

interface DBQAccountProviderDescriptionGetType : DBQueryType<URI, AccountProviderDescription?>
