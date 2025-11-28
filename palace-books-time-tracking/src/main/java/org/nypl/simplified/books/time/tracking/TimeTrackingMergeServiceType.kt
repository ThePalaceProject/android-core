package org.nypl.simplified.books.time.tracking

import java.util.concurrent.TimeUnit

/**
 * The merge service.
 *
 * This takes spans of time that were serialized by the [TimeTrackingCollectorServiceType] and
 * merges them into time tracking entries to be sent to the server. Necessarily, it only operates
 * on spans that are over a minute old, because there might still be spans to come in the current
 * minute.
 */

interface TimeTrackingMergeServiceType : AutoCloseable {

  /**
   * A testing method; the caller will block until the next tick is completed.
   */

  fun awaitTick(timeout: Long, unit: TimeUnit)
}
