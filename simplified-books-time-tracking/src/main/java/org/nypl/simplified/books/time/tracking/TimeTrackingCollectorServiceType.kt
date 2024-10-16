package org.nypl.simplified.books.time.tracking

import java.util.concurrent.TimeUnit

/**
 * The collector service.
 *
 * This listens for a stream of "time tracked" events and serializes them into a directory. It
 * also records, for debugging purposes, when time tracking has "started" and "stopped" (ie, a
 * book has opened or closed).
 */

interface TimeTrackingCollectorServiceType : AutoCloseable {

  /**
   * A testing method; the caller will block until the next write is completed.
   */

  fun awaitWrite(timeout: Long, unit: TimeUnit)
}
