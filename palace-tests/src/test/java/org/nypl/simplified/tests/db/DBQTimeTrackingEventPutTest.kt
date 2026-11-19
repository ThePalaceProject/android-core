package org.nypl.simplified.tests.db

import io.azam.ulidj.ULID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttempt
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailed
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedExceptionally
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedPermanently
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptSucceeded
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStarted
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStopped
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import java.net.URI
import java.nio.file.Path
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests for the time tracking event put query.
 *
 * The query inserts a single event into the time tracking events table. The event's class
 * determines which of the optional columns are populated: entry-related operations populate
 * the entry identifier, only the entry created operation populates the during minute, only
 * the send attempt operations populate the seconds played and, of those, only the failed
 * exceptionally operation populates the exception. The insert is strict, so an event which
 * violates one of the table's constraints, such as an entry identifier which is not a valid
 * ULID, raises an error and stores nothing.
 */
class DBQTimeTrackingEventPutTest {

  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val libraryID =
    URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b")
  private val base =
    OffsetDateTime.parse("2024-10-16T00:00:00Z")

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
  fun tearDown() {
    this.database.close()
  }

  private fun putEvent(event: TimeTrackingEvent) {
    this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
      transaction.commit()
    }
  }

  private fun selectAllEvents(): List<TimeTrackingEvent> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(
        DBQTimeTrackingEventSelectType::class.java,
        this.base.minusDays(1),
      )
    }
  }

  @Test
  fun testPutStarted() {
    val event =
      TimeTrackingStarted(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutStopped() {
    val event =
      TimeTrackingStopped(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutEntryCreated() {
    val event =
      TimeTrackingEntryCreated(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = ULID.random(),
        duringMinute = this.base.minusMinutes(1L),
        secondsPlayed = 30,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutSendAttempt() {
    val event =
      TimeTrackingSendAttempt(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = ULID.random(),
        secondsPlayed = 30,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutSendAttemptSucceeded() {
    val event =
      TimeTrackingSendAttemptSucceeded(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = ULID.random(),
        secondsPlayed = 30,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutSendAttemptFailed() {
    val event =
      TimeTrackingSendAttemptFailed(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = ULID.random(),
        secondsPlayed = 30,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutSendAttemptFailedExceptionally() {
    val event =
      TimeTrackingSendAttemptFailedExceptionally(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = ULID.random(),
        secondsPlayed = 30,
        exception = "java.lang.IllegalStateException: the connection was reset",
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutSendAttemptFailedPermanently() {
    val event =
      TimeTrackingSendAttemptFailedPermanently(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = ULID.random(),
        secondsPlayed = 30,
      )

    this.putEvent(event)

    assertEquals(listOf(event), this.selectAllEvents())
  }

  @Test
  fun testPutOfMultipleEventsReturnsThemInInsertionOrder() {
    val events =
      listOf(
        TimeTrackingStarted(
          time = this.base,
          libraryID = this.libraryID.toString(),
          bookID = this.palaceID.value,
        ),
        TimeTrackingEntryCreated(
          time = this.base.plusSeconds(1L),
          libraryID = this.libraryID.toString(),
          bookID = this.palaceID.value,
          entryID = ULID.random(),
          duringMinute = this.base.minusMinutes(1L),
          secondsPlayed = 30,
        ),
        TimeTrackingSendAttemptSucceeded(
          time = this.base.plusSeconds(2L),
          libraryID = this.libraryID.toString(),
          bookID = this.palaceID.value,
          entryID = ULID.random(),
          secondsPlayed = 30,
        ),
      )

    for (event in events) {
      this.putEvent(event)
    }

    assertEquals(events, this.selectAllEvents())
  }

  @Test
  fun testPutOfEntryCreatedWithInvalidEntryIdentifierThrows() {
    val event =
      TimeTrackingEntryCreated(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = "not-a-valid-ulid",
        duringMinute = this.base.minusMinutes(1L),
        secondsPlayed = 30,
      )

    assertThrows(SQLException::class.java) {
      this.database.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
      }
    }

    assertEquals(0, this.selectAllEvents().size)
  }

  @Test
  fun testPutOfSendAttemptWithInvalidEntryIdentifierThrows() {
    val event =
      TimeTrackingSendAttempt(
        time = this.base,
        libraryID = this.libraryID.toString(),
        bookID = this.palaceID.value,
        entryID = UUID.randomUUID().toString(),
        secondsPlayed = 30,
      )

    assertThrows(SQLException::class.java) {
      this.database.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
      }
    }

    assertEquals(0, this.selectAllEvents().size)
  }
}
