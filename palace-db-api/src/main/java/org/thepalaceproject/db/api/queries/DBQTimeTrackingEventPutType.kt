package org.thepalaceproject.db.api.queries

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.thepalaceproject.db.api.DBQueryType

/**
 * Append a single event to the time tracking event log.
 */
interface DBQTimeTrackingEventPutType : DBQueryType<TimeTrackingEvent, Unit>
