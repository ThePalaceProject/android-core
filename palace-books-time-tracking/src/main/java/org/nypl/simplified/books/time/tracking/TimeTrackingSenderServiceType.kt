package org.nypl.simplified.books.time.tracking

import java.util.concurrent.TimeUnit

/**
 * The sender service.
 *
 * This takes time tracking entries that were merged by the [TimeTrackingMergeServiceType] service
 * and sends them to the server. It then deletes entries that were successfully sent, and retries
 * entries that were not.
 */

interface TimeTrackingSenderServiceType : AutoCloseable {

  /**
   * A testing method; the caller will block until the next write is completed.
   */

  fun awaitWrite(timeout: Long, unit: TimeUnit)
}
