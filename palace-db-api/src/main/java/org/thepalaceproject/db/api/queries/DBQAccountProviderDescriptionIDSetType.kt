package org.thepalaceproject.db.api.queries

import org.thepalaceproject.db.api.DBQueryType
import java.net.URI

interface DBQAccountProviderDescriptionIDSetType : DBQueryType<Unit, Set<URI>>
