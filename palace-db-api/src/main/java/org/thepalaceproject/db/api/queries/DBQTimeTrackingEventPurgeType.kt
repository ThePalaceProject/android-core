package org.thepalaceproject.db.api.queries

import org.thepalaceproject.db.api.DBQueryType
import java.time.OffsetDateTime

/**
 * Delete all time tracking events recorded strictly before a given time.
 *
 * @param parameters The time cutoff
 * @return The number of events deleted
 */
interface DBQTimeTrackingEventPurgeType : DBQueryType<OffsetDateTime, Int>
