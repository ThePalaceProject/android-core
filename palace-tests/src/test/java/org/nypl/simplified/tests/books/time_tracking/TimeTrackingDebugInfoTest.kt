package org.nypl.simplified.tests.books.time_tracking

import io.azam.ulidj.ULID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttempt
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedExceptionally
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStarted
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStopped
import org.nypl.simplified.books.time.tracking.TimeTrackingDebugInfo
import org.nypl.simplified.books.time.tracking.TimeTrackingStatus
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests of the plain text summary of the time tracking pipeline state.
 *
 * The summary is a pure function of the database contents, the service status, and the
 * current time. These tests pin down the format of the summary: the section structure,
 * the counts, the age formatting, the staleness and stall flags, and the event window
 * and truncation.
 */
class TimeTrackingDebugInfoTest {
  private val NOW =
    OffsetDateTime.parse("2024-10-16T12:00:00Z")
  private val ACCOUNT_A =
    AccountID(UUID.randomUUID())
  private val ACCOUNT_B =
    AccountID(UUID.randomUUID())
  private val BOOK =
    PlayerPalaceID(UUID.randomUUID().toString())
  private val LIBRARY_ID =
    "urn:librarything:library:123"
  private val LIBRARY_URI =
    URI.create("https://example.org/library")
  private val TARGET_URI =
    URI.create("https://example.org/time-tracking")

  private lateinit var database: DBType
  private lateinit var directory: Path

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.directory = directory

    this.database =
      DBFactory.open(
        DBParameters(
          file = directory.resolve("database.db"),
          accountProviderParsers = AccountProviderDescriptionCollectionParsers(),
          accountProviderSerializers = AccountProviderDescriptionCollectionSerializers(),
        ),
      )
  }

  @AfterEach
  fun close() {
    this.database.close()
  }

  @Test
  fun testEmptyDatabase() {
    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertTrue(lines.contains("Session"))
    assertTrue(lines.contains("  Inactive"))
    assertTrue(lines.contains("Spans (received, not yet merged)"))
    assertTrue(lines.contains("  count: 0"))
    assertTrue(lines.contains("Entries (merged, not yet sent)"))
    assertTrue(lines.contains("Health"))
    assertTrue(lines.contains("  OK"))
    assertTrue(lines.contains("Events (last 24 hours: 0 total, showing most recent 0)"))
    assertFalse(lines.any { it.contains("WARNING") })
  }

  @Test
  fun testActiveSession() {
    val status =
      TimeTrackingStatus.Active(
        accountID = this.ACCOUNT_A,
        bookId = this.BOOK,
        libraryId = this.LIBRARY_ID,
        timeTrackingUri = this.TARGET_URI,
      )
    val lines =
      this.lines(status)

    assertTrue(lines.contains("  Active"))
    assertTrue(lines.contains("  account: " + this.ACCOUNT_A.toString()))
    assertTrue(lines.contains("  book: " + this.BOOK.value))
    assertTrue(lines.contains("  library: " + this.LIBRARY_ID))
    assertTrue(lines.contains("  uri: " + this.TARGET_URI.toString()))
  }

  @Test
  fun testSpanAgesAndStaleness() {
    this.putSpan(UUID.randomUUID(), this.NOW.minusSeconds(10L))
    this.putSpan(UUID.randomUUID(), this.NOW.minusMinutes(3L))
    this.putSpan(UUID.randomUUID(), this.NOW.minusMinutes(5L))

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertTrue(lines.contains("  count: 3"))
    assertTrue(lines.contains("  oldest received: 5m 0s ago"))
    assertTrue(lines.contains("  older than 2 minutes: 2 (expected 0)"))
  }

  @Test
  fun testSpanAgesFormatting() {
    this.putSpan(UUID.randomUUID(), this.NOW.minusSeconds(7L))
    val lines =
      this.lines(TimeTrackingStatus.Inactive)
    assertTrue(lines.contains("  oldest received: 7s ago"))

    this.putSpan(UUID.randomUUID(), this.NOW.minusSeconds(307L))
    this.putSpan(UUID.randomUUID(), this.NOW.minusHours(4L).minusMinutes(2L))
    val linesWithOlderSpans =
      this.lines(TimeTrackingStatus.Inactive)
    assertTrue(linesWithOlderSpans.contains("  oldest received: 4h 2m 0s ago"))
  }

  @Test
  fun testEntryTotalsAndPerAccount() {
    this.putEntry(ULID.random(), this.NOW.minusMinutes(5L), 30, this.ACCOUNT_A)
    this.putEntry(ULID.random(), this.NOW.minusMinutes(1L), 45, this.ACCOUNT_A)
    this.putEntry(ULID.random(), this.NOW.minusMinutes(2L), 60, this.ACCOUNT_B)

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertTrue(lines.contains("  count: 3"))
    assertTrue(lines.contains("  total: 135 seconds (2.2 min)"))
    assertTrue(lines.contains("  oldest during-minute: 5m 0s ago"))
    assertTrue(lines.contains("  by account:"))
    assertTrue(lines.contains("    " + this.ACCOUNT_A + ": 2 entries, 75 seconds"))
    assertTrue(lines.contains("    " + this.ACCOUNT_B + ": 1 entries, 60 seconds"))
  }

  @Test
  fun testStallWarningWhenNoRecentAttempts() {
    this.putEntry(ULID.random(), this.NOW.minusMinutes(1L), 30, this.ACCOUNT_A)

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertTrue(
      lines.contains(
        "  WARNING: entries are pending, but there have been no send attempts in the last 10 minutes",
      ),
    )
  }

  @Test
  fun testNoStallWarningWhenRecentAttempt() {
    this.putEntry(ULID.random(), this.NOW.minusMinutes(1L), 30, this.ACCOUNT_A)
    this.putEvent(
      TimeTrackingSendAttempt(
        time = this.NOW.minusMinutes(1L),
        libraryID = this.LIBRARY_ID,
        bookID = this.BOOK.value,
        entryID = ULID.random(),
        secondsPlayed = 30,
      ),
    )

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertFalse(lines.any { it.contains("WARNING") })
    assertTrue(lines.contains("  OK"))
  }

  @Test
  fun testStallWarningNotTriggeredWithoutEntries() {
    this.putEvent(
      TimeTrackingStarted(
        time = this.NOW.minusMinutes(1L),
        libraryID = this.LIBRARY_ID,
        bookID = this.BOOK.value,
      ),
    )

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertFalse(lines.any { it.contains("WARNING") })
    assertTrue(lines.contains("  OK"))
  }

  @Test
  fun testEventsOutsideWindowExcluded() {
    this.putEvent(
      TimeTrackingStarted(
        time = this.NOW.minusHours(25L),
        libraryID = this.LIBRARY_ID,
        bookID = this.BOOK.value,
      ),
    )

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertTrue(lines.contains("Events (last 24 hours: 0 total, showing most recent 0)"))
  }

  @Test
  fun testRecentEventsTruncatedToTwenty() {
    for (i in 0 until 25) {
      val time =
        this.NOW.minusSeconds((25L - i) * 30L)
      if (i % 2 == 0) {
        this.putEvent(
          TimeTrackingStarted(
            time = time,
            libraryID = this.LIBRARY_ID,
            bookID = this.BOOK.value,
          ),
        )
      } else {
        this.putEvent(
          TimeTrackingStopped(
            time = time,
            libraryID = this.LIBRARY_ID,
            bookID = this.BOOK.value,
          ),
        )
      }
    }

    val lines =
      this.lines(TimeTrackingStatus.Inactive)

    assertTrue(lines.contains("Events (last 24 hours: 25 total, showing most recent 20)"))
    val eventLines =
      this.eventLines(lines)
    assertEquals(20, eventLines.size)
  }

  @Test
  fun testEventLineContainsFields() {
    val entryID =
      ULID.random()
    this.putEvent(
      TimeTrackingEntryCreated(
        time = this.NOW.minusMinutes(1L),
        libraryID = this.LIBRARY_ID,
        bookID = this.BOOK.value,
        entryID = entryID,
        duringMinute = this.NOW.minusMinutes(1L),
        secondsPlayed = 30,
      ),
    )
    this.putEvent(
      TimeTrackingSendAttemptFailedExceptionally(
        time = this.NOW.minusMinutes(2L),
        libraryID = this.LIBRARY_ID,
        bookID = this.BOOK.value,
        entryID = entryID,
        secondsPlayed = 30,
        exception = "java.net.ConnectException: connection refused",
      ),
    )

    val lines =
      this.lines(TimeTrackingStatus.Inactive)
    val eventLines =
      this.eventLines(lines)
    assertEquals(2, eventLines.size)

    val entryCreatedLine =
      eventLines.first { it.contains("TimeTrackingEntryCreated") }
    assertTrue(entryCreatedLine.contains("2024-10-16T11:59Z"))
    assertTrue(entryCreatedLine.contains("book=" + this.BOOK.value))
    assertTrue(entryCreatedLine.contains("entry=" + entryID))
    assertTrue(entryCreatedLine.contains("seconds=30"))

    val exceptionLine =
      eventLines.first { it.contains("TimeTrackingSendAttemptFailedExceptionally") }
    assertTrue(exceptionLine.contains("exception=java.net.ConnectException: connection refused"))
  }

  private fun lines(status: TimeTrackingStatus): List<String> {
    return TimeTrackingDebugInfo
      .lines(
        database = this.database,
        status = status,
        now = this.NOW,
      )
  }

  private fun eventLines(lines: List<String>): List<String> {
    val headerIndex =
      lines.indexOfFirst { it.startsWith("Events ") }
    assertTrue(headerIndex >= 0)
    return lines.subList(headerIndex + 1, lines.size)
  }

  private fun putSpan(id: UUID, received: OffsetDateTime) {
    val span =
      TimeTrackingReceivedSpan(
        id = id,
        accountID = this.ACCOUNT_A,
        libraryID = this.LIBRARY_URI,
        bookID = this.BOOK,
        timeStarted = received.minusMinutes(1L),
        timeEnded = received,
        timeReceived = received,
        targetURI = this.TARGET_URI,
      )
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
        transaction.commit()
      }
    }
  }

  private fun putEntry(
    id: String,
    duringMinute: OffsetDateTime,
    seconds: Int,
    account: AccountID,
  ) {
    val entry =
      TimeTrackingEntryOutgoing(
        accountID = account,
        libraryID = this.LIBRARY_URI,
        bookID = this.BOOK,
        targetURI = this.TARGET_URI,
        timeEntry =
          TimeTrackingEntry(
            id = id,
            duringMinute = duringMinute,
            secondsPlayed = seconds,
          ),
      )
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
        transaction.commit()
      }
    }
  }

  private fun putEvent(event: TimeTrackingEvent) {
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
        transaction.commit()
      }
    }
  }

}
