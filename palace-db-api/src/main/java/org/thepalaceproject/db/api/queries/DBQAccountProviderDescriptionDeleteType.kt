package org.thepalaceproject.db.api.queries

import org.thepalaceproject.db.api.DBQueryType
import java.net.URI

interface DBQAccountProviderDescriptionDeleteType : DBQueryType<Set<URI>, Unit>
