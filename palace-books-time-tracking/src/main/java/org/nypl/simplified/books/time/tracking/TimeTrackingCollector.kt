package org.nypl.simplified.books.time.tracking

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.Observable
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The collector service.
 *
 * This listens for a stream of "time tracked" events and records them as spans in the
 * database. It also records, in the event log, when time tracking has "started" and
 * "stopped" (ie, a book has opened or closed).
 */

class TimeTrackingCollector private constructor(
  private val database: DBType,
  private val profiles: ProfilesControllerType,
  private val status: AttributeReadableType<TimeTrackingStatus>,
  private val timeSegments: Observable<PlayerTimeTracked>,
  private val audit: TimeTrackingAudit,
  private val clock: () -> OffsetDateTime,
  private val diagnostics: DiagnosticsServiceType,
) : TimeTrackingCollectorServiceType {
  private val logger =
    LoggerFactory.getLogger(TimeTrackingCollector::class.java)

  private val awaitWrite =
    LinkedBlockingQueue<Unit>()
  private val resources =
    CloseableCollection.create()

  private val executor =
    Executors.newSingleThreadExecutor { r ->
      val thread = Thread(r)
      thread.name = "org.nypl.simplified.books.time.tracking.collector[${thread.id}]"
      thread.isDaemon = true
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  init {
    this.resources.add(
      AutoCloseable {
        this.executor.shutdown()
        this.executor.awaitTermination(30L, TimeUnit.SECONDS)
      },
    )
    val timeSubscription = this.timeSegments.subscribe(this::onTimeTrackedReceived)
    this.resources.add(AutoCloseable { timeSubscription.dispose() })
    this.resources.add(this.status.subscribe(this::onStatusChanged))
    this.resources.add(AutoCloseable { this.awaitWrite.offer(Unit) })
  }

  private fun onTimeTrackedReceived(time: PlayerTimeTracked) {
    this.executor.execute { this.saveTimeTracked(time) }
  }

  private fun saveTimeTracked(time: PlayerTimeTracked) {
    try {
      when (val statusNow = this.status.get()) {
        is TimeTrackingStatus.Active -> {
          val account = this.profiles.profileCurrent().account(statusNow.accountID)

          if (time.bookTrackingId != statusNow.bookId) {
            this.diagnostics.report(
              code = DiagnosticCodes.TIME_TRACKING_LOSS,
              data =
                mapOf(
                  Pair("Reason", "bookMismatch"),
                  Pair("Book", time.bookTrackingId.value),
                  Pair("ActiveBook", statusNow.bookId.value),
                  Pair("Seconds", time.seconds.toString()),
                ),
            )
            this.logger.warn(
              "Time loss: Time tracking data received for book {}, but book {} is selected",
              statusNow.bookId,
              time.bookTrackingId,
            )
            return
          }

          /*
           * A span whose end time does not lie strictly after its start time carries no usable
           * playback data; it is the product of a corrupted player report. It is rejected here
           * so that it cannot reach the merge, where a negative duration would violate the
           * entry constraint and stall the pipeline for every book and account.
           */

          if (!time.timeEnded.isAfter(time.timeStarted)) {
            this.diagnostics.report(
              code = DiagnosticCodes.TIME_TRACKING_LOSS,
              data =
                mapOf(
                  Pair("Reason", "nonPositiveDuration"),
                  Pair("Book", statusNow.bookId.value),
                  Pair("TimeStarted", time.timeStarted.toString()),
                  Pair("TimeEnded", time.timeEnded.toString()),
                ),
            )
            this.logger.warn(
              "Time loss: span for book {} has a non-positive duration",
              statusNow.bookId,
            )
            return
          }

          /*
           * The start and end times are stored with their original offset preserved. The
           * minute to which an entry applies (and which is sent to the server) is derived
           * from the start time, so preserving the offset keeps the wire format identical
           * to that of the legacy file-based implementation. The receive time, by contrast,
           * comes from the application clock (UTC) and is used only for the database merge
           * threshold, so it is normalized to UTC by the storage layer.
           */

          val span =
            TimeTrackingReceivedSpan(
              id = time.id,
              accountID = statusNow.accountID,
              libraryID = account.provider.id,
              bookID = statusNow.bookId,
              timeStarted = time.timeStarted,
              timeEnded = time.timeEnded,
              timeReceived = this.clock.invoke(),
              targetURI = statusNow.timeTrackingUri,
            )

          this.database.openConnection().use { connection ->
            connection.openTransaction().use { transaction ->
              transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
              transaction.commit()
            }
          }
        }

        TimeTrackingStatus.Inactive -> {
          this.diagnostics.report(
            code = DiagnosticCodes.TIME_TRACKING_LOSS,
            data =
              mapOf(
                Pair("Reason", "noActiveBook"),
                Pair("Book", time.bookTrackingId.value),
                Pair("Seconds", time.seconds.toString()),
              ),
          )
          this.logger.warn(
            "Time tracking data received for book {}, but no book is selected",
            time.bookTrackingId,
          )
        }
      }
    } catch (e: Throwable) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_LOSS,
        data =
          mapOf(
            Pair("Reason", "saveFailed"),
            Pair("Book", time.bookTrackingId.value),
            Pair("Seconds", time.seconds.toString()),
          ),
        throwable = e,
      )
      this.logger.warn("Failed to save time tracking information: ", e)
    } finally {
      this.awaitWrite.offer(Unit)
    }
  }

  private fun onStatusChanged(
    oldValue: TimeTrackingStatus,
    newValue: TimeTrackingStatus,
  ) {
    when (newValue) {
      is TimeTrackingStatus.Active -> {
        this.audit.timeTrackingStarted(
          libraryID = newValue.libraryId,
          bookID = newValue.bookId.value,
        )
      }

      TimeTrackingStatus.Inactive -> {
        if (oldValue is TimeTrackingStatus.Active) {
          this.audit.timeTrackingStopped(
            libraryID = oldValue.libraryId,
            bookID = oldValue.bookId.value,
          )
        }
      }
    }
  }

  companion object {
    /**
     * Create a new collector.
     *
     * @param database The database
     * @param profiles The profiles controller
     * @param status The time tracking status attribute
     * @param timeSegments The observable stream of time tracked segments
     * @param audit The event log
     * @param clock The clock
     * @param diagnostics The diagnostics service
     *
     * @return The collector
     */
    fun create(
      database: DBType,
      profiles: ProfilesControllerType,
      status: AttributeReadableType<TimeTrackingStatus>,
      timeSegments: Observable<PlayerTimeTracked>,
      audit: TimeTrackingAudit,
      clock: () -> OffsetDateTime,
      diagnostics: DiagnosticsServiceType,
    ): TimeTrackingCollectorServiceType =
      TimeTrackingCollector(
        database = database,
        profiles = profiles,
        status = status,
        timeSegments = timeSegments,
        audit = audit,
        clock = clock,
        diagnostics = diagnostics,
      )
  }

  override fun awaitWrite(
    timeout: Long,
    unit: TimeUnit,
  ) {
    this.awaitWrite.poll(timeout, unit)
  }

  override fun close() {
    this.resources.close()
  }
}
