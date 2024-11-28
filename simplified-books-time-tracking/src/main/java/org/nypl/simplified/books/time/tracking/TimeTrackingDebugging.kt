package org.nypl.simplified.books.time.tracking

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock

object TimeTrackingDebugging {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingDebugging::class.java)

  private val separator =
    ByteArray(2)

  private val fileLock =
    ReentrantLock()

  init {
    this.separator[0] = '\n'.code.toByte()
    this.separator[1] = 0
  }

  private fun writeLocked(
    directory: File,
    properties: Properties
  ) {
    this.fileLock.lock()

    try {
      val file = File(directory, "time_tracking_debug.dat")
      FileOutputStream(file, true).use { stream ->
        properties.storeToXML(stream, "")
        stream.write(this.separator)
        stream.flush()
      }
    } catch (e: Throwable) {
      try {
        MDC.put("TimeTracking", "true")
        this.logger.error("Failed to log time tracking operation: ", e)
      } finally {
        MDC.remove("TimeTracking")
      }
    } finally {
      this.fileLock.unlock()
    }
  }

  fun onTimeTrackingStarted(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingStarted")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    this.writeLocked(timeTrackingDebugDirectory, p)
  }

  fun onTimeTrackingStopped(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingStopped")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    this.writeLocked(timeTrackingDebugDirectory, p)
  }

  fun onTimeTrackingEntryCreated(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String,
    entryId: String,
    duringMinute: String,
    seconds: Int,
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingEntryCreated")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("EntryID", entryId)
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    p.setProperty("DuringMinute", duringMinute)
    p.setProperty("Seconds", seconds.toString())
    this.writeLocked(timeTrackingDebugDirectory, p)
  }

  fun onTimeTrackingSendAttempt(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String,
    entryId: String,
    seconds: Int
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingSendAttempt")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    p.setProperty("EntryID", entryId)
    p.setProperty("Seconds", seconds.toString())
    this.writeLocked(timeTrackingDebugDirectory, p)
  }

  fun onTimeTrackingSendAttemptSucceeded(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String,
    entryId: String
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingSendAttemptSucceeded")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    p.setProperty("EntryID", entryId)
    this.writeLocked(timeTrackingDebugDirectory, p)
  }

  fun onTimeTrackingSendAttemptFailedExceptionally(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String,
    entryId: String,
    exception: Throwable
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingSendAttemptFailedExceptionally")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    p.setProperty("EntryID", entryId)
    p.setProperty("Exception", exceptionTextOf(exception))
    this.writeLocked(timeTrackingDebugDirectory, p)
  }

  private fun exceptionTextOf(
    exception: Throwable
  ): String {
    return StringWriter().use { stringWriter ->
      PrintWriter(stringWriter).use { printWriter ->
        exception.printStackTrace(printWriter)
        printWriter.flush()
        stringWriter.toString()
      }
    }
  }

  fun onTimeTrackingSendAttemptFailed(
    timeTrackingDebugDirectory: File,
    libraryId: String,
    bookId: String,
    entryId: String
  ) {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingSendAttemptFailed")
    p.setProperty("Time", OffsetDateTime.now(UTC).toString())
    p.setProperty("LibraryID", libraryId)
    p.setProperty("BookID", bookId)
    p.setProperty("EntryID", entryId)
    this.writeLocked(timeTrackingDebugDirectory, p)
  }
}
