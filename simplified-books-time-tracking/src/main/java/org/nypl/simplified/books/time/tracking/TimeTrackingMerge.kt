package org.nypl.simplified.books.time.tracking

import com.io7m.jmulticlose.core.CloseableCollection
import io.azam.ulidj.ULID
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * The merge service.
 *
 * This takes spans of time that were serialized by the [TimeTrackingCollectorServiceType] and
 * merges them into time tracking entries to be sent to the server. Necessarily, it only operates
 * on spans that are over a minute old, because there might still be spans to come in the current
 * minute.
 */

class TimeTrackingMerge private constructor(
  private val clock: () -> OffsetDateTime,
  private val inboxDirectory: Path,
  private val outboxDirectory: Path,
  private val frequency: Duration,
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
    this.resources.add(AutoCloseable {
      this.executor.shutdown()
      this.executor.awaitTermination(30L, TimeUnit.SECONDS)
    })

    this.executor.scheduleWithFixedDelay(
      { this.tick() },
      0L,
      this.frequency.toMillis(),
      TimeUnit.MILLISECONDS
    )

    this.resources.add(AutoCloseable { this.tickWait.offer(Unit) })
  }

  private fun isSpanFileSuitable(
    timeOldest: Instant,
    file: Path
  ): Boolean {
    if (!Files.isRegularFile(file)) {
      return false
    }
    if (!file.toString().endsWith(".ttspan")) {
      return false
    }

    val fileTime = Files.getLastModifiedTime(file).toInstant()
    return fileTime.isBefore(timeOldest)
  }

  private fun tick() {
    try {
      MDC.put("System", "TimeTracking")
      MDC.put("SubSystem", "Merge")

      val timeNow =
        this.clock.invoke()
          .toInstant()
      val timeOldest =
        timeNow.minusSeconds(90L)

      /*
       * Collect every non-temporary file with a modification date that shows that the file is
       * at least 90 seconds old. By only inspecting files that are at least this old, we know
       * that we won't receive any new spans that fell within the minutes of those spans. This
       * means that we can safely merge them into single time tracking entries without the risk
       * of losing any time.
       */

      val spanFiles: List<Path> =
        Files.list(this.inboxDirectory)
          .filter { p -> isSpanFileSuitable(timeOldest, p) }
          .collect(Collectors.toList())

      val spans = mutableListOf<TimeTrackingReceivedSpan>()
      for (file in spanFiles) {
        spans.add(TimeTrackingReceivedSpan.ofFile(file))
      }

      /*
       * Merge the recorded spans into series of time tracking entries. This will, for example,
       * merge spans that occurred within the same minute into a single time tracking entry, and
       * split spans that crossed a minute boundary. Then, record each entry.
       */

      val entries = mergeEntries(spans)
      for (entry in entries) {
        this.writeEntry(entry)
      }

      /*
       * Now delete the spans that were actually used.
       */

      for (span in spans) {
        this.deleteSpan(span)
      }
    } catch (e: Throwable) {
      this.logger.error("Failed to process time tracking entries: ", e)
    } finally {
      this.tickWait.offer(Unit)
    }
  }

  private fun deleteSpan(
    span: TimeTrackingReceivedSpan
  ) {
    val file = this.inboxDirectory.resolve("${span.id}.ttspan")
    this.logger.debug("Deleting span {}", file)
    Files.deleteIfExists(file)
  }

  private fun writeEntry(
    entry: TimeTrackingEntryOutgoing
  ) {
    val fileTmp =
      this.outboxDirectory.resolve("${entry.timeEntry.id}.tteo.tmp")
    val file =
      this.outboxDirectory.resolve("${entry.timeEntry.id}.tteo")

    this.logger.debug("Writing entry {}", file)
    Files.newOutputStream(fileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
      entry.toProperties().store(s, "")
      s.flush()
    }
    Files.move(
      fileTmp,
      file,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )
  }

  companion object {

    private data class MergeKey(
      val accountID: AccountID,
      val bookID: PlayerPalaceID,
      val libraryID: URI,
      val targetURI: URI
    )

    fun mergeEntries(
      spans: List<TimeTrackingReceivedSpan>
    ): List<TimeTrackingEntryOutgoing> {
      val spansByKey =
        mutableMapOf<MergeKey, MutableList<TimeTrackingReceivedSpan>>()

      for (span in spans) {
        val key = MergeKey(
          accountID = span.accountID,
          bookID = span.bookID,
          libraryID = span.libraryID,
          targetURI = span.targetURI
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
      keySpans: MutableList<TimeTrackingReceivedSpan>
    ): Collection<TimeTrackingEntryOutgoing> {
      keySpans.sortBy { e -> e.timeStarted }

      val secondsForMinute =
        mutableMapOf<OffsetDateTime, Long>()

      for (span in keySpans) {
        val spanSeconds =
          Duration.between(span.timeStarted, span.timeEnded)
            .toMillis() / 1_000L

        val crossesMinuteBoundary =
          span.timeStarted.minute != span.timeEnded.minute

        val minuteCurr =
          span.timeStarted.withSecond(0)
        val minuteNext =
          minuteCurr.plusMinutes(1L)

        if (crossesMinuteBoundary) {
          val addNext = span.timeEnded.second
          val addCurr = 60 - span.timeStarted.second
          secondsForMinute[minuteCurr] =
            Math.min(60, (secondsForMinute[minuteCurr] ?: 0) + addCurr)
          secondsForMinute[minuteNext] =
            Math.min(60, (secondsForMinute[minuteNext] ?: 0) + addNext)
        } else {
          secondsForMinute[minuteCurr] =
            Math.min(60, (secondsForMinute[minuteCurr] ?: 0) + spanSeconds)
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
            timeEntry = TimeTrackingEntry(
              id = ULID.random(),
              duringMinute = minute.toString(),
              secondsPlayed = seconds.toInt()
            ),
          )
        )
      }
      return results.toList()
    }

    fun create(
      clock: () -> OffsetDateTime,
      frequency: Duration,
      inputDirectory: Path,
      outputDirectory: Path,
    ): TimeTrackingMergeServiceType {
      return TimeTrackingMerge(
        clock = clock,
        frequency = frequency,
        inboxDirectory = inputDirectory,
        outboxDirectory = outputDirectory
      )
    }
  }

  override fun awaitTick(
    timeout: Long,
    unit: TimeUnit
  ) {
    this.tickWait.poll(timeout, unit)
  }

  override fun close() {
    this.resources.close()
  }
}
