package org.thepalaceproject.db.api.queries

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.thepalaceproject.db.api.DBQueryType

/**
 * Insert a single time tracking entry.
 *
 * The operation is idempotent with respect to the entry identifier; inserting an entry with
 * an identifier that is already present is a no-op.
 */
interface DBQTimeTrackingEntryPutType : DBQueryType<TimeTrackingEntryOutgoing, Int>
