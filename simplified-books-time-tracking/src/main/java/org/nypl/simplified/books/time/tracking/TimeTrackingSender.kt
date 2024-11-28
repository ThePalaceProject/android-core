package org.nypl.simplified.books.time.tracking

import com.io7m.jmulticlose.core.CloseableCollection
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * The sender service.
 *
 * The sender service attempts to send all time tracking entries that have been serialized into
 * a directory by the [TimeTrackingCollector] service. It is responsible for merging entries into
 * single requests in order to avoid overwhelming the remote side with lots of small requests, and
 * is responsible for deleting serialized tracking entries when the remote side has accepted
 * them.
 */

class TimeTrackingSender private constructor(
  private val profiles: ProfilesControllerType,
  private val httpCalls: TimeTrackingHTTPCallsType,
  private val debugDirectory: Path,
  private val inputDirectory: Path,
  private val frequency: Duration,
) : TimeTrackingSenderServiceType {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingSender::class.java)

  private val tickWrite =
    LinkedBlockingQueue<Unit>()
  private val resources =
    CloseableCollection.create()

  private val executor =
    Executors.newSingleThreadScheduledExecutor { r ->
      val thread = Thread(r)
      thread.name = "org.nypl.simplified.books.time.tracking.sender[${thread.id}]"
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
      this::onTrySend,
      0L,
      this.frequency.toMillis(),
      TimeUnit.MILLISECONDS
    )
    this.resources.add(AutoCloseable {
      this.tickWrite.offer(Unit)
    })
  }

  private fun isFileSuitable(
    file: Path
  ): Boolean {
    if (!Files.isRegularFile(file)) {
      return false
    }
    return file.toString().endsWith(".tteo")
  }

  private fun onTrySend() {
    try {
      MDC.put("System", "TimeTracking")
      MDC.put("SubSystem", "Sender")
      MDC.put("TimeLoss", "false")

      val entryFiles: List<Path> =
        Files.list(this.inputDirectory).use { inputStream ->
          inputStream.filter { p -> this.isFileSuitable(p) }
            .collect(Collectors.toList())
        }

      val entries = mutableListOf<TimeTrackingEntryOutgoing>()
      for (entryFile in entryFiles) {
        try {
          entries.add(TimeTrackingEntryOutgoing.ofFile(entryFile))
        } catch (e: Throwable) {
          MDC.put("TimeLoss", "true")
          this.logger.warn("Unable to parse local time tracking entry: ", e)
        }
      }

      val grouped = TimeTrackingEntryOutgoing.group(entries)
      for ((key, outgoingEntries) in grouped) {
        check(outgoingEntries.isNotEmpty()) {
          "Outgoing entries cannot be empty"
        }
        this.sendOneBatch(key, outgoingEntries)
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed to send time tracking entries: ", e)
    }
  }

  private fun sendOneBatch(
    key: TimeTrackingEntryOutgoing.Key,
    outgoingEntries: List<TimeTrackingEntryOutgoing>
  ) {
    try {
      Files.createDirectories(this.debugDirectory)
      Files.createDirectories(this.inputDirectory)

      outgoingEntries.forEach { e ->
        TimeTrackingDebugging.onTimeTrackingSendAttempt(
          timeTrackingDebugDirectory = this.debugDirectory.toFile(),
          libraryId = key.libraryID.toString(),
          bookId = key.bookID.value,
          entryId = e.timeEntry.id,
          seconds = e.timeEntry.secondsPlayed
        )
      }

      val account =
        this.profiles.profileCurrent().account(key.accountID)

      val response =
        this.httpCalls.registerTimeTrackingInfo(
          account = account,
          request = TimeTrackingRequest(
            bookId = key.bookID.value,
            libraryId = key.libraryID,
            timeTrackingUri = key.targetURI,
            timeEntries = outgoingEntries.map { e -> e.timeEntry }
          )
        )

      /*
       * If the responses list is empty, and the count suggests that all entries succeeded, we
       * log success and delete the entry. Unfortunately, the spec leaves open the possibility
       * that there will be a non-zero number of failures, and nothing in the responses list. In
       * that edge case, we won't know _which_ entries failed and so we can do nothing other than
       * send every entry again. The server is responsible for rejecting any entry that it has
       * already received, so this should be safe.
       */

      if (response.responses.isEmpty()) {
        if (response.summary.successes == outgoingEntries.size && response.summary.failures == 0) {
          outgoingEntries.forEach { e -> this.entrySentSuccessfully(e) }
          return
        }

        check(response.summary.failures != 0)
        outgoingEntries.forEach { e ->
          TimeTrackingDebugging.onTimeTrackingSendAttemptFailed(
            timeTrackingDebugDirectory = this.debugDirectory.toFile(),
            libraryId = key.libraryID.toString(),
            bookId = key.bookID.value,
            entryId = e.timeEntry.id
          )
        }
        return
      }

      /*
       * For each successful response, we need to log the successful send attempt and then delete
       * the entry so that it isn't sent again. For each failed attempt, we simply log the failure.
       */

      for (r in response.responses) {
        val e = outgoingEntries.firstOrNull { entry -> entry.timeEntry.id == r.id }
        if (r.isStatusSuccess() || r.isStatusGone()) {
          if (e != null) {
            this.entrySentSuccessfully(e)
          }
        } else {
          if (e != null) {
            TimeTrackingDebugging.onTimeTrackingSendAttemptFailed(
              timeTrackingDebugDirectory = this.debugDirectory.toFile(),
              libraryId = key.libraryID.toString(),
              bookId = key.bookID.value,
              entryId = e.timeEntry.id
            )
          }
        }
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed to send time tracking entries: ", e)
      outgoingEntries.forEach { entry ->
        TimeTrackingDebugging.onTimeTrackingSendAttemptFailedExceptionally(
          timeTrackingDebugDirectory = this.debugDirectory.toFile(),
          libraryId = key.libraryID.toString(),
          bookId = key.bookID.value,
          entryId = entry.timeEntry.id,
          exception = e
        )
      }
    } finally {
      this.tickWrite.offer(Unit)
    }
  }

  private fun entrySentSuccessfully(
    entry: TimeTrackingEntryOutgoing
  ) {
    TimeTrackingDebugging.onTimeTrackingSendAttemptSucceeded(
      timeTrackingDebugDirectory = this.debugDirectory.toFile(),
      libraryId = entry.libraryID.toString(),
      bookId = entry.bookID.value,
      entryId = entry.timeEntry.id
    )

    Files.deleteIfExists(
      this.inputDirectory.resolve("${entry.timeEntry.id}.tteo")
    )
  }

  companion object {
    fun create(
      profiles: ProfilesControllerType,
      httpCalls: TimeTrackingHTTPCallsType,
      debugDirectory: Path,
      inputDirectory: Path,
      frequency: Duration,
    ): TimeTrackingSenderServiceType {
      return TimeTrackingSender(
        profiles = profiles,
        httpCalls = httpCalls,
        debugDirectory = debugDirectory,
        inputDirectory = inputDirectory,
        frequency = frequency
      )
    }
  }

  override fun awaitWrite(
    timeout: Long,
    unit: TimeUnit
  ) {
    this.tickWrite.poll(timeout, unit)
  }

  override fun close() {
    this.resources.close()
  }
}
