package org.nypl.simplified.tests.db

import io.azam.ulidj.ULID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptSucceeded
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStarted
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests for the time tracking event select query.
 *
 * The query selects the events which were recorded at or after a given cutoff time, in the
 * order in which they were recorded. The cutoff is inclusive: an event recorded exactly at
 * the cutoff time is selected. The order is the order of insertion, not the order of the
 * recorded times.
 */
class DBQTimeTrackingEventSelectTest {

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

  private fun makeStarted(time: OffsetDateTime): TimeTrackingStarted {
    return TimeTrackingStarted(
      time = time,
      libraryID = this.libraryID.toString(),
      bookID = this.palaceID.value,
    )
  }

  private fun makeEntryCreated(time: OffsetDateTime): TimeTrackingEntryCreated {
    return TimeTrackingEntryCreated(
      time = time,
      libraryID = this.libraryID.toString(),
      bookID = this.palaceID.value,
      entryID = ULID.random(),
      duringMinute = this.base.minusMinutes(1L),
      secondsPlayed = 30,
    )
  }

  private fun makeSendAttemptSucceeded(
    time: OffsetDateTime,
  ): TimeTrackingSendAttemptSucceeded {
    return TimeTrackingSendAttemptSucceeded(
      time = time,
      libraryID = this.libraryID.toString(),
      bookID = this.palaceID.value,
      entryID = ULID.random(),
      secondsPlayed = 30,
    )
  }

  private fun putEvent(event: TimeTrackingEvent) {
    this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
      transaction.commit()
    }
  }

  private fun selectEvents(cutoff: OffsetDateTime): List<TimeTrackingEvent> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingEventSelectType::class.java, cutoff)
    }
  }

  @Test
  fun testSelectFromEmptyTableReturnsEmptyList() {
    assertEquals(0, this.selectEvents(this.base).size)
  }

  @Test
  fun testSelectCutoffIsInclusive() {
    val event =
      this.makeStarted(this.base)

    this.putEvent(event)

    assertEquals(listOf(event), this.selectEvents(this.base))
    assertEquals(0, this.selectEvents(this.base.plusSeconds(1L)).size)
  }

  @Test
  fun testSelectExcludesEventsRecordedBeforeCutoff() {
    val events =
      listOf(
        this.makeStarted(this.base),
        this.makeEntryCreated(this.base.plusSeconds(1L)),
        this.makeSendAttemptSucceeded(this.base.plusSeconds(2L)),
      )

    for (event in events) {
      this.putEvent(event)
    }

    assertEquals(
      events.subList(1, 3),
      this.selectEvents(this.base.plusSeconds(1L))
    )
  }

  @Test
  fun testSelectOrdersByInsertionOrderNotByRecordedTime() {
    val later =
      this.makeStarted(this.base.plusSeconds(2L))
    val earlier =
      this.makeEntryCreated(this.base.plusSeconds(1L))

    this.putEvent(later)
    this.putEvent(earlier)

    assertEquals(listOf(later, earlier), this.selectEvents(this.base))
  }

  @Test
  fun testSelectPreservesAllFields() {
    val events =
      listOf(
        this.makeStarted(this.base),
        this.makeEntryCreated(this.base.plusSeconds(1L)),
        this.makeSendAttemptSucceeded(this.base.plusSeconds(2L)),
      )

    for (event in events) {
      this.putEvent(event)
    }

    assertEquals(events, this.selectEvents(this.base))
  }
}
