package org.nypl.simplified.books.time.tracking

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttempt
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailed
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedExceptionally
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedPermanently
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptSucceeded
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStarted
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStopped
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Properties

/**
 * The migration of the legacy file-based time tracking data into the database.
 *
 * Before this migration, time tracking data was stored as files in a directory tree:
 *
 * - The `collector` subdirectory: one file per raw span, named `<id>.ttspan`, in Java
 *   properties format.
 * - The `sender` subdirectory: one file per merged entry, named `<id>.tteo`, in Java
 *   properties format.
 * - The `debug` subdirectory: the event log, in a file named `time_tracking_debug.dat`, as a
 *   concatenation of XML properties documents.
 *
 * The migration is idempotent: inserts are keyed on the span and entry identifiers with
 * conflict-ignore semantics, so a partial re-run of the migration is a no-op for data that
 * has already been migrated.
 *
 * The legacy files are left untouched until the migration has been committed to the
 * database; if the process fails before the commit, the files remain and the migration is
 * retried on a later start-up. After a successful commit, the migrated files are deleted.
 * Files that cannot be parsed are left in place and reported to the diagnostics service, so
 * that they can be inspected and recovered manually; they are never deleted.
 *
 * The event log is an exception to the "never delete" rule: a torn tail record (the result
 * of a crash mid-write of the final record) is skipped with a diagnostic, and the whole
 * file is deleted after a successful commit. The event log is an audit aid, and its data is
 * not required for the settlement of time.
 */
class TimeTrackingMigration private constructor(
  private val database: DBType,
  private val legacyDirectory: Path,
  private val diagnostics: DiagnosticsServiceType,
) {
  private val logger =
    LoggerFactory.getLogger(TimeTrackingMigration::class.java)

  /**
   * Run the migration.
   *
   * This method is idempotent and safe to run on every start-up; it is a no-op if there is
   * no legacy directory or if the legacy data has already been migrated.
   */
  fun migrate() {
    if (!Files.isDirectory(this.legacyDirectory)) {
      return
    }

    val spans = mutableListOf<TimeTrackingReceivedSpan>()
    val spanFiles = mutableListOf<Path>()

    val entries = mutableListOf<TimeTrackingEntryOutgoing>()
    val entryFiles = mutableListOf<Path>()

    val events = mutableListOf<TimeTrackingEvent>()
    var auditFile: Path? = null

    val collectorDirectory = this.legacyDirectory.resolve("collector")
    if (Files.isDirectory(collectorDirectory)) {
      Files.list(collectorDirectory).use { stream ->
        stream
          .filter { p -> Files.isRegularFile(p) && p.fileName.toString().endsWith(".ttspan") }
          .forEach { file -> this.migrateSpanFile(file, spans, spanFiles) }
      }
    }

    val senderDirectory = this.legacyDirectory.resolve("sender")
    if (Files.isDirectory(senderDirectory)) {
      Files.list(senderDirectory).use { stream ->
        stream
          .filter { p -> Files.isRegularFile(p) && p.fileName.toString().endsWith(".tteo") }
          .forEach { file -> this.migrateEntryFile(file, entries, entryFiles) }
      }
    }

    val debugDirectory = this.legacyDirectory.resolve("debug")
    if (Files.isDirectory(debugDirectory)) {
      val auditFileCandidate = debugDirectory.resolve("time_tracking_debug.dat")
      if (Files.isRegularFile(auditFileCandidate)) {
        auditFile = auditFileCandidate
        this.migrateAuditFile(auditFileCandidate, events)
      }
    }

    if (spans.isEmpty() && entries.isEmpty() && events.isEmpty() && auditFile == null) {
      this.deleteEmptyDirectoryTree()
      return
    }

    /*
     * Insert all of the migrated data in a single transaction. The inserts are
     * idempotent, so a re-run of the migration after a partial failure is a no-op for data
     * that has already been committed.
     */

    var committed = false
    try {
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          for (span in spans) {
            transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
          }
          for (entry in entries) {
            transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
          }
          for (event in events) {
            transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
          }
          transaction.commit()
        }
      }
      committed = true
    } catch (e: Throwable) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_MIGRATION,
        data =
          mapOf(
            Pair("Reason", "commitFailed"),
            Pair("Spans", spans.size.toString()),
            Pair("Entries", entries.size.toString()),
            Pair("Events", events.size.toString()),
          ),
        throwable = e,
      )
      this.logger.error("Failed to commit time tracking migration: ", e)
      return
    }

    if (committed) {
      for (file in spanFiles) {
        this.deleteFile(file)
      }
      for (file in entryFiles) {
        this.deleteFile(file)
      }
      val file = auditFile
      if (file != null) {
        this.deleteFile(file)
      }
      this.deleteEmptyDirectoryTree()
    }
  }

  private fun migrateSpanFile(
    file: Path,
    spans: MutableList<TimeTrackingReceivedSpan>,
    spanFiles: MutableList<Path>,
  ) {
    try {
      val properties = Properties()
      Files.newInputStream(file).use { stream ->
        properties.load(stream)
      }
      if (properties.getProperty("@Type") != "TimeTrackingReceivedSpan") {
        throw IllegalArgumentException("Unexpected @Type '${properties.getProperty("@Type")}'")
      }

      val timeReceived =
        Files
          .getLastModifiedTime(file)
          .toInstant()
          .atOffset(ZoneOffset.UTC)

      spans.add(TimeTrackingReceivedSpan.ofProperties(properties, timeReceived))
      spanFiles.add(file)
    } catch (e: Throwable) {
      this.reportFailure("spanParseFailed", file, e)
    }
  }

  private fun migrateEntryFile(
    file: Path,
    entries: MutableList<TimeTrackingEntryOutgoing>,
    entryFiles: MutableList<Path>,
  ) {
    try {
      val properties = Properties()
      Files.newInputStream(file).use { stream ->
        properties.load(stream)
      }
      if (properties.getProperty("@Type") != "TimeTrackingEntryOutgoing") {
        throw IllegalArgumentException("Unexpected @Type '${properties.getProperty("@Type")}'")
      }

      entries.add(TimeTrackingEntryOutgoing.ofProperties(properties))
      entryFiles.add(file)
    } catch (e: Throwable) {
      this.reportFailure("entryParseFailed", file, e)
    }
  }

  /**
   * Migrate the legacy event log.
   *
   * The legacy event log is a concatenation of XML properties documents, separated by a
   * newline and a NUL byte. Each complete document is parsed and converted to an event. A
   * partial document at the end of the file (the result of a crash mid-write) cannot be
   * parsed; it is skipped and reported.
   *
   * @param file The event log file
   * @param events The list to which parsed events are appended
   */
  private fun migrateAuditFile(
    file: Path,
    events: MutableList<TimeTrackingEvent>,
  ) {
    val data: ByteArray
    try {
      data = Files.readAllBytes(file)
    } catch (e: Throwable) {
      this.reportFailure("auditReadFailed", file, e)
      return
    }

    val text = String(data, Charsets.UTF_8)
    val terminator = "</properties>"
    val pieces = text.split(terminator)

    /*
     * Every piece except the last is a complete document (the terminator has been split
     * away). The last piece is either just a trailing separator (the file ends cleanly)
     * or a torn tail.
     *
     * The pieces that hold complete documents are prefixed by the separator that followed
     * the previous document (a newline and a NUL byte). That prefix is not part of the XML
     * document and must be stripped before parsing; it is removed by locating the start of
     * the XML declaration. Splitting on the terminator, rather than on the separator, means
     * that a document written but whose trailing separator was never flushed (a crash in the
     * tiny window between the two writes) is still recovered.
     */

    for (index in 0 until pieces.size - 1) {
      val document = this.extractDocument(pieces[index] + terminator)
      if (document == null) {
        continue
      }
      try {
        val properties = Properties()
        ByteArrayInputStream(document.toByteArray(Charsets.UTF_8)).use { stream ->
          properties.loadFromXML(stream)
        }
        val event = this.parseAuditRecord(properties)
        if (event != null) {
          events.add(event)
        }
      } catch (e: Throwable) {
        this.reportFailure("auditParseFailed", file, e)
      }
    }

    val tail = pieces[pieces.size - 1]
    if (this.isTornTail(tail)) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_MIGRATION,
        data =
          mapOf(
            Pair("Reason", "auditTornTail"),
            Pair("File", file.fileName.toString()),
            Pair("TailLength", tail.length.toString()),
          ),
      )
      this.logger.warn(
        "Skipping torn tail of legacy time tracking event log ({} characters)",
        tail.length,
      )
    }
  }

  /**
   * Locate and return the XML document within a piece, discarding any leading separator
   * bytes. Returns null if the piece does not contain an XML declaration.
   *
   * @param piece The piece, including the re-appended terminator
   *
   * @return The XML document, or null
   */
  private fun extractDocument(piece: String): String? {
    val start = piece.indexOf("<?xml")
    if (start < 0) {
      return null
    }
    return piece.substring(start)
  }

  /**
   * Determine whether the final piece of the split file is a torn tail: a partial XML
   * document that was being written when the process was interrupted. A clean file ending
   * (just a trailing separator) is not a torn tail.
   *
   * @param tail The final piece
   *
   * @return True if the tail is a partial document
   */
  private fun isTornTail(tail: String): Boolean {
    return tail.indexOf("<?xml") >= 0
  }

  private fun parseAuditRecord(properties: Properties): TimeTrackingEvent? {
    val operationText = properties.getProperty("Operation")
    if (operationText == null) {
      return null
    }

    val timeText = properties.getProperty("Time")
    if (timeText == null) {
      return null
    }
    val time = TimeTrackingTime.parse(timeText)

    val libraryID = properties.getProperty("LibraryID")
    if (libraryID == null) {
      return null
    }
    val bookID = properties.getProperty("BookID")
    if (bookID == null) {
      return null
    }

    val entryID = properties.getProperty("EntryID")

    val duringMinuteText = properties.getProperty("DuringMinute")
    val duringMinute: OffsetDateTime?
    if (duringMinuteText == null) {
      duringMinute = null
    } else {
      duringMinute = TimeTrackingTime.parse(duringMinuteText)
    }

    val secondsText = properties.getProperty("Seconds")
    val secondsPlayed: Int?
    if (secondsText == null) {
      secondsPlayed = null
    } else {
      secondsPlayed = secondsText.toInt()
    }

    val exception = properties.getProperty("Exception")

    return when (operationText) {
      TimeTrackingStarted.OPERATION_NAME -> {
        TimeTrackingStarted(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
        )
      }

      TimeTrackingStopped.OPERATION_NAME -> {
        TimeTrackingStopped(
          time = time,
          libraryID = libraryID,
          bookID = bookID,
        )
      }

      TimeTrackingEntryCreated.OPERATION_NAME -> {
        if (entryID == null || duringMinute == null || secondsPlayed == null) {
          null
        } else {
          TimeTrackingEntryCreated(
            time = time,
            libraryID = libraryID,
            bookID = bookID,
            entryID = entryID,
            duringMinute = duringMinute,
            secondsPlayed = secondsPlayed,
          )
        }
      }

      TimeTrackingSendAttempt.OPERATION_NAME -> {
        if (entryID == null || secondsPlayed == null) {
          null
        } else {
          TimeTrackingSendAttempt(
            time = time,
            libraryID = libraryID,
            bookID = bookID,
            entryID = entryID,
            secondsPlayed = secondsPlayed,
          )
        }
      }

      TimeTrackingSendAttemptSucceeded.OPERATION_NAME -> {
        if (entryID == null || secondsPlayed == null) {
          null
        } else {
          TimeTrackingSendAttemptSucceeded(
            time = time,
            libraryID = libraryID,
            bookID = bookID,
            entryID = entryID,
            secondsPlayed = secondsPlayed,
          )
        }
      }

      TimeTrackingSendAttemptFailed.OPERATION_NAME -> {
        if (entryID == null || secondsPlayed == null) {
          null
        } else {
          TimeTrackingSendAttemptFailed(
            time = time,
            libraryID = libraryID,
            bookID = bookID,
            entryID = entryID,
            secondsPlayed = secondsPlayed,
          )
        }
      }

      TimeTrackingSendAttemptFailedExceptionally.OPERATION_NAME -> {
        if (entryID == null || secondsPlayed == null || exception == null) {
          null
        } else {
          TimeTrackingSendAttemptFailedExceptionally(
            time = time,
            libraryID = libraryID,
            bookID = bookID,
            entryID = entryID,
            secondsPlayed = secondsPlayed,
            exception = exception,
          )
        }
      }

      TimeTrackingSendAttemptFailedPermanently.OPERATION_NAME -> {
        if (entryID == null || secondsPlayed == null) {
          null
        } else {
          TimeTrackingSendAttemptFailedPermanently(
            time = time,
            libraryID = libraryID,
            bookID = bookID,
            entryID = entryID,
            secondsPlayed = secondsPlayed,
          )
        }
      }

      else -> {
        null
      }
    }
  }

  private fun reportFailure(
    reason: String,
    file: Path,
    exception: Throwable,
  ) {
    this.diagnostics.report(
      code = DiagnosticCodes.TIME_TRACKING_MIGRATION,
      data =
        mapOf(
          Pair("Reason", reason),
          Pair("File", file.fileName.toString()),
        ),
      throwable = exception,
    )
    this.logger.warn(
      "Failed to migrate legacy time tracking file {} ({}): ",
      file.fileName,
      reason,
      exception,
    )
  }

  private fun deleteFile(file: Path) {
    try {
      Files.deleteIfExists(file)
    } catch (e: Throwable) {
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_MIGRATION,
        data =
          mapOf(
            Pair("Reason", "deleteFailed"),
            Pair("File", file.fileName.toString()),
          ),
        throwable = e,
      )
      this.logger.warn("Failed to delete migrated legacy time tracking file {}: ", file, e)
    }
  }

  private fun deleteEmptyDirectoryTree() {
    for (
    directory in
    listOf(
      this.legacyDirectory.resolve("debug"),
      this.legacyDirectory.resolve("collector"),
      this.legacyDirectory.resolve("sender"),
      this.legacyDirectory,
    )
    ) {
      try {
        if (Files.isDirectory(directory)) {
          Files.list(directory).use { stream ->
            if (!stream.findAny().isPresent()) {
              Files.deleteIfExists(directory)
            }
          }
        }
      } catch (e: Throwable) {
        /*
         * Best effort; a leftover empty directory is harmless.
         */
        this.logger.debug("Failed to delete empty legacy time tracking directory {}: ", directory, e)
      }
    }
  }

  companion object {
    /**
     * Create a new migration object.
     *
     * @param database The database
     * @param legacyDirectory The root of the legacy file-based time tracking data, ie,
     *   `<data>/time_tracking`
     * @param diagnostics The diagnostics service
     *
     * @return The migration object
     */
    fun create(
      database: DBType,
      legacyDirectory: Path,
      diagnostics: DiagnosticsServiceType,
    ): TimeTrackingMigration =
      TimeTrackingMigration(
        database = database,
        legacyDirectory = legacyDirectory,
        diagnostics = diagnostics,
      )
  }
}
