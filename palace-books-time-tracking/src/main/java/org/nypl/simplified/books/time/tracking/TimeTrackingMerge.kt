package org.nypl.simplified.books.time.tracking

import com.io7m.jmulticlose.core.CloseableCollection
import io.azam.ulidj.ULID
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The merge service.
 *
 * This takes spans of time that have been recorded by the [TimeTrackingCollectorServiceType]
 * and merges them into time tracking entries to be sent to the server. Necessarily, it only
 * operates on spans that are over a minute old, because there might still be spans to come in
 * the current minute.
 *
 * The selection of spans, the insertion of the merged entries, and the deletion of the spans
 * are performed in a single database transaction. If the process fails at any point, the
 * transaction is rolled back and the spans are retried on a later tick; no time can be lost.
 */

class TimeTrackingMerge private constructor(
  private val database: DBType,
  private val clock: () -> OffsetDateTime,
  private val frequency: Duration,
  private val diagnostics: DiagnosticsServiceType,
) : TimeTrackingMergeServiceType {
  private val logger =
    LoggerFactory.getLogger(TimeTrackingMerge::class.java)
  private val resources =
    CloseableCollection.create()
  private val tickWait =
    LinkedBlockingQueue<Unit>(1)

  private val executor =
    Executors.newSingleThreadScheduledExecutor { r ->
      val thread = Thread(r)
      thread.name = "org.nypl.simplified.books.time.tracking.merge[${thread.id}]"
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

    this.executor.scheduleWithFixedDelay(
      { this.tick() },
      0L,
      this.frequency.toMillis(),
      TimeUnit.MILLISECONDS,
    )

    this.resources.add(AutoCloseable { this.tickWait.offer(Unit) })
  }

  private fun tick() {
    var committedInvalidSpans: List<TimeTrackingReceivedSpan> = emptyList()
    try {
      val timeNow =
        this.clock.invoke()
      val timeOldest =
        timeNow.minusSeconds(90L)

      /*
       * Select every span that was received at least 90 seconds ago. By only inspecting spans
       * that are at least this old, we know that we won't receive any new spans that fell
       * within the minutes of those spans. This means that we can safely merge them into
       * single time tracking entries without the risk of losing any time.
       *
       * The selection, the insertion of the merged entries, and the deletion of the spans are
       * performed in a single transaction.
       */

      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          val spans =
            transaction.execute(DBQTimeTrackingSpanSelectType::class.java, timeOldest)

          if (spans.isNotEmpty()) {
            /*
             * A span whose end time does not lie strictly after its start time carries no usable
             * playback data; it is the product of a corrupted player report. Left in the table,
             * it would violate the entry constraint on every tick and stall the pipeline for every
             * book and account. Such spans are discarded and their presence is reported as a loss.
             * The collector rejects these spans at insert time; this guard also covers spans that
             * entered the table via the legacy-file migration.
             */

            val validSpans =
              spans.filter { s -> s.timeEnded.isAfter(s.timeStarted) }
            val invalidSpans =
              spans.filter { s -> !s.timeEnded.isAfter(s.timeStarted) }

            if (validSpans.isNotEmpty()) {
              val entries = TimeTrackingMerge.mergeEntries(validSpans)
              for (entry in entries) {
                transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
                transaction.execute(
                  DBQTimeTrackingEventPutType::class.java,
                  TimeTrackingEntryCreated(
                    time = timeNow,
                    libraryID = entry.libraryID.toString(),
                    bookID = entry.bookID.value,
                    entryID = entry.timeEntry.id,
                    duringMinute = entry.timeEntry.duringMinute,
                    secondsPlayed = entry.timeEntry.secondsPlayed,
                  ),
                )
              }
            }

            val spanIds =
              spans.map { s -> s.id.toString() }
            transaction.execute(DBQTimeTrackingSpanDeleteType::class.java, spanIds)
            transaction.commit()
            committedInvalidSpans = invalidSpans
          }
        }
      }

      /*
       * The loss report is issued only after the transaction has been committed and the
       * connection closed. The diagnostics service performs external work (the loaded
       * implementation reports to a crash reporting service), and the database writer lock
       * must not be held across that work. If the transaction fails, the invalid spans
       * remain in the database and will be reported by a later tick.
       */

      if (committedInvalidSpans.isNotEmpty()) {
        this.reportInvalidSpans(committedInvalidSpans)
      }
    } catch (e: Throwable) {
      this.logger.error("Failed to process time tracking entries: ", e)
    } finally {
      this.tickWait.offer(Unit)
    }
  }

  /**
   * Report the given spans, which carry no usable playback data, as a loss.
   *
   * The spans have already been deleted from the database by the caller; this only
   * records their presence so that the loss is observable.
   *
   * @param spans The invalid spans
   */

  private fun reportInvalidSpans(spans: List<TimeTrackingReceivedSpan>) {
    for (span in spans) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_LOSS,
        data =
          mapOf(
            Pair("Reason", "invalidSpanDuration"),
            Pair("Book", span.bookID.value),
            Pair("TimeStarted", span.timeStarted.toString()),
            Pair("TimeEnded", span.timeEnded.toString()),
          ),
      )
    }
    this.logger.warn(
      "Discarded {} time tracking span(s) with a non-positive duration",
      spans.size,
    )
  }

  companion object {
    private data class MergeKey(
      val accountID: AccountID,
      val bookID: PlayerPalaceID,
      val libraryID: URI,
      val targetURI: URI,
    )

    /**
     * Merge a list of spans into time tracking entries.
     *
     * This will, for example, merge spans that occurred within the same minute into a single
     * time tracking entry, and split spans that crossed a minute boundary.
     *
     * @param spans The spans
     *
     * @return The merged entries
     */
    fun mergeEntries(spans: List<TimeTrackingReceivedSpan>): List<TimeTrackingEntryOutgoing> {
      val spansByKey =
        mutableMapOf<MergeKey, MutableList<TimeTrackingReceivedSpan>>()

      for (span in spans) {
        val key =
          MergeKey(
            accountID = span.accountID,
            bookID = span.bookID,
            libraryID = span.libraryID,
            targetURI = span.targetURI,
          )
        var existing = spansByKey.get(key)
        if (existing == null) {
          existing = mutableListOf()
        }
        existing.add(span)
        spansByKey.put(key, existing)
      }

      val results = mutableListOf<TimeTrackingEntryOutgoing>()
      for ((key, keySpans) in spansByKey) {
        results.addAll(mergeSpansForKey(key, keySpans))
      }
      return results.toList()
    }

    private fun mergeSpansForKey(
      key: MergeKey,
      keySpans: MutableList<TimeTrackingReceivedSpan>,
    ): Collection<TimeTrackingEntryOutgoing> {
      keySpans.sortBy { e -> e.timeStarted }

      val secondsForMinute =
        mutableMapOf<OffsetDateTime, Long>()

      for (span in keySpans) {
        /*
         * Spans with a non-positive duration carry no data. They should have been discarded by
         * the tick before reaching here; this guard is defensive and also covers direct callers
         * of mergeEntries.
         */

        if (!span.timeEnded.isAfter(span.timeStarted)) {
          continue
        }

        /*
         * Walk the span one minute at a time, crediting each minute it touches with the number
         * of whole seconds of the span that fall within it. This correctly attributes time to
         * every minute the span crosses, including spans that cross more than one minute
         * boundary. A minute is never credited with more than sixty seconds, so that
         * overlapping spans cannot report more time than actually elapsed.
         */

        var minuteStart =
          span.timeStarted.truncatedTo(ChronoUnit.MINUTES)
        while (minuteStart.isBefore(span.timeEnded)) {
          val minuteEnd =
            minuteStart.plusMinutes(1L)
          val overlapStart =
            if (span.timeStarted.isAfter(minuteStart)) {
              span.timeStarted
            } else {
              minuteStart
            }
          val overlapEnd =
            if (span.timeEnded.isBefore(minuteEnd)) {
              span.timeEnded
            } else {
              minuteEnd
            }
          if (overlapEnd.isAfter(overlapStart)) {
            val secondsInMinute =
              Duration
                .between(overlapStart, overlapEnd)
                .toMillis() /
                1_000L
            secondsForMinute[minuteStart] =
              Math.min(60L, (secondsForMinute[minuteStart] ?: 0L) + secondsInMinute)
          }
          minuteStart = minuteEnd
        }
      }

      val results = mutableListOf<TimeTrackingEntryOutgoing>()
      for ((minute, seconds) in secondsForMinute) {
        results.add(
          TimeTrackingEntryOutgoing(
            accountID = key.accountID,
            bookID = key.bookID,
            targetURI = key.targetURI,
            libraryID = key.libraryID,
            timeEntry =
              TimeTrackingEntry(
                id = ULID.random(),
                duringMinute = minute,
                secondsPlayed = seconds.toInt(),
              ),
          ),
        )
      }
      return results.toList()
    }

    /**
     * Create a new merge service.
     *
     * @param database The database
     * @param clock The clock
     * @param frequency The frequency of ticks
     * @param diagnostics The diagnostics service
     *
     * @return The merge service
     */
    fun create(
      database: DBType,
      clock: () -> OffsetDateTime,
      frequency: Duration,
      diagnostics: DiagnosticsServiceType,
    ): TimeTrackingMergeServiceType =
      TimeTrackingMerge(
        database = database,
        clock = clock,
        frequency = frequency,
        diagnostics = diagnostics,
      )
  }

  override fun awaitTick(
    timeout: Long,
    unit: TimeUnit,
  ) {
    this.tickWait.poll(timeout, unit)
  }

  override fun close() {
    this.resources.close()
  }
}
