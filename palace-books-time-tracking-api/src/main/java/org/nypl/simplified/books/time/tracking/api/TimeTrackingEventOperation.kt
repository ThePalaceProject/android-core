package org.nypl.simplified.books.time.tracking.api

/**
 * The operations recorded in the time tracking event log.
 */
enum class TimeTrackingEventOperation {
  /**
   * Time tracking for a book has begun.
   */
  TimeTrackingStarted,

  /**
   * Time tracking for a book has ended.
   */
  TimeTrackingStopped,

  /**
   * A time tracking entry has been created by the merger.
   */
  TimeTrackingEntryCreated,

  /**
   * An attempt to send a time tracking entry has been made.
   */
  TimeTrackingSendAttempt,

  /**
   * An attempt to send a time tracking entry has succeeded.
   */
  TimeTrackingSendAttemptSucceeded,

  /**
   * An attempt to send a time tracking entry has failed, but may be retried.
   */
  TimeTrackingSendAttemptFailed,

  /**
   * An attempt to send a time tracking entry has failed due to an unexpected exception, and
   * may be retried.
   */
  TimeTrackingSendAttemptFailedExceptionally,

  /**
   * An attempt to send a time tracking entry has failed permanently, and will not be retried.
   */
  TimeTrackingSendAttemptFailedPermanently,
}
