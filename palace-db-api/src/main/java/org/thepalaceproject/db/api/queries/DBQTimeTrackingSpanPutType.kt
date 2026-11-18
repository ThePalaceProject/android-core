package org.thepalaceproject.db.api.queries

import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.thepalaceproject.db.api.DBQueryType

/**
 * Insert a single time tracking span.
 *
 * The operation is idempotent with respect to the span identifier; inserting a span with an
 * identifier that is already present is a no-op.
 */
interface DBQTimeTrackingSpanPutType : DBQueryType<TimeTrackingReceivedSpan, Int>
