package org.thepalaceproject.db.api.queries

import org.thepalaceproject.db.api.DBQueryType

/**
 * Delete the given time tracking entries, by their identifiers.
 *
 * This is the operation that acknowledges successful delivery of entries to the server.
 *
 * @param parameters The identifiers of the entries to delete
 * @return The number of entries deleted
 */
interface DBQTimeTrackingEntryDeleteType : DBQueryType<List<String>, Int>
