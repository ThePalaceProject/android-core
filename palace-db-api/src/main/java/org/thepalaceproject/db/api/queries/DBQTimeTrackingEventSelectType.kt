package org.thepalaceproject.db.api.queries

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.thepalaceproject.db.api.DBQueryType
import java.time.OffsetDateTime

/**
 * Select all time tracking events recorded at or after a given time, in chronological order.
 *
 * @param parameters The time cutoff
 * @return The events
 */
interface DBQTimeTrackingEventSelectType : DBQueryType<OffsetDateTime, List<TimeTrackingEvent>>
