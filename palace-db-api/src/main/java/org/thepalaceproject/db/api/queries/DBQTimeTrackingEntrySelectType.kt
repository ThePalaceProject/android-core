package org.thepalaceproject.db.api.queries

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.thepalaceproject.db.api.DBQueryType

/**
 * Select all pending time tracking entries, in no guaranteed order.
 */
interface DBQTimeTrackingEntrySelectType : DBQueryType<Unit, List<TimeTrackingEntryOutgoing>>
