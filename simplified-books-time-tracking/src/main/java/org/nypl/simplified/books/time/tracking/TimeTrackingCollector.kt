package org.nypl.simplified.books.time.tracking

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.Observable
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The collector service.
 *
 * This listens for a stream of "time tracked" events and serializes them into a directory. It
 * also records, for debugging purposes, when time tracking has "started" and "stopped" (ie, a
 * book has opened or closed).
 */

class TimeTrackingCollector private constructor(
  private val profiles: ProfilesControllerType,
  private val status: AttributeReadableType<TimeTrackingStatus>,
  private val timeSegments: Observable<PlayerTimeTracked>,
  private val debugDirectory: Path,
  private val outputDirectory: Path,
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
    this.resources.add(AutoCloseable {
      this.executor.shutdown()
      this.executor.awaitTermination(30L, TimeUnit.SECONDS)
    })
    val timeSubscription = this.timeSegments.subscribe(this::onTimeTrackedReceived)
    this.resources.add(AutoCloseable { timeSubscription.dispose() })
    this.resources.add(this.status.subscribe(this::onStatusChanged))
    this.resources.add(AutoCloseable { this.awaitWrite.offer(Unit) })
  }

  private fun onTimeTrackedReceived(
    time: PlayerTimeTracked
  ) {
    this.executor.execute { this.saveTimeTracked(time) }
  }

  private fun saveTimeTracked(
    time: PlayerTimeTracked
  ) {
    try {
      MDC.put("System", "TimeTracking")
      MDC.put("SubSystem", "Collector")
      MDC.put("Book", time.bookTrackingId.value)
      MDC.put("Seconds", time.seconds.toString())
      MDC.remove("TimeLoss")

      when (val statusNow = this.status.get()) {
        is TimeTrackingStatus.Active -> {
          val account = this.profiles.profileCurrent().account(statusNow.accountID)

          if (time.bookTrackingId != statusNow.bookId) {
            MDC.put("TimeLoss", "true")
            this.logger.warn(
              "Time loss: Time tracking data received for book {}, but book {} is selected",
              statusNow.bookId,
              time.bookTrackingId
            )
            return
          }

          Files.createDirectories(this.outputDirectory)

          val outFile =
            this.outputDirectory.resolve("${time.id}.ttspan")
          val outFileTmp =
            this.outputDirectory.resolve("${time.id}.ttspan.tmp")

          val utcStart =
            time.timeStarted.withOffsetSameInstant(ZoneOffset.UTC)
          val utcEnd =
            time.timeEnded.withOffsetSameInstant(ZoneOffset.UTC)

          val span =
            TimeTrackingReceivedSpan(
              id = time.id,
              accountID = statusNow.accountID,
              libraryID = account.provider.id,
              bookID = statusNow.bookId,
              timeStarted = utcStart,
              timeEnded = utcEnd,
              targetURI = statusNow.timeTrackingUri
            )

          Files.newOutputStream(outFileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
            span.toProperties().store(s, "")
            s.flush()
          }
          Files.move(outFileTmp, outFile, ATOMIC_MOVE, REPLACE_EXISTING)
        }

        TimeTrackingStatus.Inactive -> {
          MDC.put("TimeLoss", "true")
          this.logger.warn(
            "Time tracking data received for book {}, but no book is selected",
            time.bookTrackingId
          )
        }
      }
    } catch (e: Throwable) {
      MDC.put("TimeLoss", "true")
      this.logger.warn("Failed to save time tracking information: ", e)
    } finally {
      this.awaitWrite.offer(Unit)
    }
  }

  companion object {
    fun create(
      profiles: ProfilesControllerType,
      status: AttributeReadableType<TimeTrackingStatus>,
      timeSegments: Observable<PlayerTimeTracked>,
      debugDirectory: Path,
      outputDirectory: Path,
    ): TimeTrackingCollectorServiceType {
      return TimeTrackingCollector(
        profiles = profiles,
        status = status,
        timeSegments = timeSegments,
        debugDirectory = debugDirectory,
        outputDirectory = outputDirectory
      )
    }
  }

  private fun onStatusChanged(
    oldValue: TimeTrackingStatus,
    newValue: TimeTrackingStatus,
  ) {
    when (newValue) {
      is TimeTrackingStatus.Active -> {
        TimeTrackingDebugging.onTimeTrackingStarted(
          timeTrackingDebugDirectory = this.debugDirectory.toFile(),
          libraryId = newValue.libraryId,
          bookId = newValue.bookId.value
        )
      }

      TimeTrackingStatus.Inactive -> {
        if (oldValue is TimeTrackingStatus.Active) {
          TimeTrackingDebugging.onTimeTrackingStopped(
            timeTrackingDebugDirectory = this.debugDirectory.toFile(),
            libraryId = oldValue.libraryId,
            bookId = oldValue.bookId.value
          )
        }
      }
    }
  }

  override fun awaitWrite(
    timeout: Long,
    unit: TimeUnit
  ) {
    this.awaitWrite.poll(timeout, unit)
  }

  override fun close() {
    this.resources.close()
  }
}
