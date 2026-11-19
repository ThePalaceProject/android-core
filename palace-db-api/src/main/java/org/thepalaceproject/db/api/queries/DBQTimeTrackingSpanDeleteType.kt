package org.thepalaceproject.db.api.queries

import org.thepalaceproject.db.api.DBQueryType

/**
 * Delete the given time tracking spans, by their identifiers.
 *
 * @param parameters The identifiers of the spans to delete
 * @return The number of spans deleted
 */
interface DBQTimeTrackingSpanDeleteType : DBQueryType<List<String>, Int>
