package org.nypl.simplified.books.time.tracking

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEventOperation
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPurgeType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime

/**
 * The time tracking event log.
 *
 * The event log is an append-only audit trail of the operations performed by the time tracking
 * pipeline. It is an aid to diagnosis and settlement; a failure to record an event is reported
 * to the diagnostics service but does not affect the integrity of the time tracking data.
 *
 * Events are written to the database, one per transaction. The event recording for an entry
 * creation is not written here; it is recorded by the merge service in the same transaction as
 * the creation of the entry itself.
 */

class TimeTrackingAudit private constructor(
  private val database: DBType,
  private val clock: () -> OffsetDateTime,
  private val diagnostics: DiagnosticsServiceType,
) {
  private val logger =
    LoggerFactory.getLogger(TimeTrackingAudit::class.java)

  /**
   * Record that time tracking has begun for a book.
   *
   * @param libraryID The identifier of the account provider
   * @param bookID The identifier of the book
   */
  fun timeTrackingStarted(
    libraryID: String,
    bookID: String,
  ) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingStarted,
      libraryID = libraryID,
      bookID = bookID,
      entryID = null,
      duringMinute = null,
      secondsPlayed = null,
      exception = null,
    )
  }

  /**
   * Record that time tracking has ended for a book.
   *
   * @param libraryID The identifier of the account provider
   * @param bookID The identifier of the book
   */
  fun timeTrackingStopped(
    libraryID: String,
    bookID: String,
  ) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingStopped,
      libraryID = libraryID,
      bookID = bookID,
      entryID = null,
      duringMinute = null,
      secondsPlayed = null,
      exception = null,
    )
  }

  /**
   * Record an attempt to send an entry.
   *
   * @param entry The entry
   */
  fun sendAttempt(entry: TimeTrackingEntryOutgoing) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingSendAttempt,
      libraryID = entry.libraryID.toString(),
      bookID = entry.bookID.value,
      entryID = entry.timeEntry.id,
      duringMinute = null,
      secondsPlayed = entry.timeEntry.secondsPlayed,
      exception = null,
    )
  }

  /**
   * Record that an attempt to send an entry has succeeded.
   *
   * @param entry The entry
   */
  fun sendAttemptSucceeded(entry: TimeTrackingEntryOutgoing) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingSendAttemptSucceeded,
      libraryID = entry.libraryID.toString(),
      bookID = entry.bookID.value,
      entryID = entry.timeEntry.id,
      duringMinute = null,
      secondsPlayed = entry.timeEntry.secondsPlayed,
      exception = null,
    )
  }

  /**
   * Record that an attempt to send an entry has failed, but may be retried.
   *
   * @param entry The entry
   */
  fun sendAttemptFailed(entry: TimeTrackingEntryOutgoing) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingSendAttemptFailed,
      libraryID = entry.libraryID.toString(),
      bookID = entry.bookID.value,
      entryID = entry.timeEntry.id,
      duringMinute = null,
      secondsPlayed = entry.timeEntry.secondsPlayed,
      exception = null,
    )
  }

  /**
   * Record that an attempt to send an entry has failed due to an unexpected exception, and
   * may be retried.
   *
   * @param entry The entry
   * @param exception The exception
   */
  fun sendAttemptFailedExceptionally(
    entry: TimeTrackingEntryOutgoing,
    exception: Throwable,
  ) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingSendAttemptFailedExceptionally,
      libraryID = entry.libraryID.toString(),
      bookID = entry.bookID.value,
      entryID = entry.timeEntry.id,
      duringMinute = null,
      secondsPlayed = entry.timeEntry.secondsPlayed,
      exception = this.exceptionTextOf(exception),
    )
  }

  /**
   * Record that an attempt to send an entry has failed permanently, and will not be retried.
   *
   * @param entry The entry
   */
  fun sendAttemptFailedPermanently(entry: TimeTrackingEntryOutgoing) {
    this.appendEvent(
      operation = TimeTrackingEventOperation.TimeTrackingSendAttemptFailedPermanently,
      libraryID = entry.libraryID.toString(),
      bookID = entry.bookID.value,
      entryID = entry.timeEntry.id,
      duringMinute = null,
      secondsPlayed = entry.timeEntry.secondsPlayed,
      exception = null,
    )
  }

  /**
   * Delete all events recorded before a given time.
   *
   * @param olderThan The time cutoff
   *
   * @return The number of events deleted
   */
  fun purgeOldEvents(olderThan: OffsetDateTime): Int {
    try {
      return this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          val deleted =
            transaction.execute(DBQTimeTrackingEventPurgeType::class.java, olderThan)
          transaction.commit()
          deleted
        }
      }
    } catch (e: Throwable) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_AUDIT,
        data =
          mapOf(
            Pair("Operation", "purgeOldEvents"),
          ),
        throwable = e,
      )
      this.logger.warn("Failed to purge old time tracking events: ", e)
      return 0
    }
  }

  private fun appendEvent(
    operation: TimeTrackingEventOperation,
    libraryID: String,
    bookID: String,
    entryID: String?,
    duringMinute: OffsetDateTime?,
    secondsPlayed: Int?,
    exception: String?,
  ) {
    val event =
      TimeTrackingEvent(
        operation = operation,
        time = this.clock.invoke(),
        libraryID = libraryID,
        bookID = bookID,
        entryID = entryID,
        duringMinute = duringMinute,
        secondsPlayed = secondsPlayed,
        exception = exception,
      )

    try {
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
          transaction.commit()
        }
      }
    } catch (e: Throwable) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_AUDIT,
        data =
          mapOf(
            Pair("Operation", operation.name),
          ),
        throwable = e,
      )
      this.logger.warn("Failed to record time tracking event: ", e)
    }
  }

  private fun exceptionTextOf(exception: Throwable): String {
    val stringWriter = StringWriter()
    PrintWriter(stringWriter).use { printWriter ->
      exception.printStackTrace(printWriter)
      printWriter.flush()
    }
    return stringWriter.toString()
  }

  companion object {
    /**
     * Create a new event log.
     *
     * @param database The database
     * @param clock The clock
     * @param diagnostics The diagnostics service
     *
     * @return The event log
     */
    fun create(
      database: DBType,
      clock: () -> OffsetDateTime,
      diagnostics: DiagnosticsServiceType,
    ): TimeTrackingAudit =
      TimeTrackingAudit(
        database = database,
        clock = clock,
        diagnostics = diagnostics,
      )
  }
}
