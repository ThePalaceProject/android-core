package org.thepalaceproject.db.api.queries

import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.thepalaceproject.db.api.DBQueryType
import java.time.OffsetDateTime

/**
 * Select all time tracking spans that were received strictly before a given time.
 *
 * @param parameters The receive time cutoff
 * @return The spans, in no guaranteed order
 */
interface DBQTimeTrackingSpanSelectType : DBQueryType<OffsetDateTime, List<TimeTrackingReceivedSpan>>
