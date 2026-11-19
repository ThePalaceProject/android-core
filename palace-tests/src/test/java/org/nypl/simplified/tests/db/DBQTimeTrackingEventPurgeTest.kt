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
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPurgeType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests for the time tracking event purge query.
 *
 * The query deletes the events which were recorded before a given cutoff time and returns
 * the number of rows deleted. The cutoff is exclusive: an event recorded exactly at the
 * cutoff time is kept.
 */
class DBQTimeTrackingEventPurgeTest {

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

  private fun putEvents(times: List<OffsetDateTime>): List<TimeTrackingEvent> {
    val events =
      times.mapIndexed { index, time ->
        when (index % 3) {
          0 -> this.makeStarted(time)
          1 -> this.makeEntryCreated(time)
          else -> this.makeSendAttemptSucceeded(time)
        }
      }

    for (event in events) {
      this.putEvent(event)
    }

    return events
  }

  private fun purgeEvents(cutoff: OffsetDateTime): Int {
    return this.database.openTransaction().use { transaction ->
      val result =
        transaction.execute(DBQTimeTrackingEventPurgeType::class.java, cutoff)
      transaction.commit()
      result
    }
  }

  private fun selectRemainingEvents(): List<TimeTrackingEvent> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(
        DBQTimeTrackingEventSelectType::class.java,
        this.base.minusDays(1),
      )
    }
  }

  @Test
  fun testPurgeFromEmptyTableReturnsZero() {
    assertEquals(0, this.purgeEvents(this.base.plusDays(1)))
  }

  @Test
  fun testPurgeWithCutoffBeforeAllEventsDeletesNothing() {
    val events =
      this.putEvents(
        listOf(this.base, this.base.plusSeconds(1L), this.base.plusSeconds(2L))
      )

    val deleted =
      this.purgeEvents(this.base.minusSeconds(1L))

    assertEquals(0, deleted)
    assertEquals(events, this.selectRemainingEvents())
  }

  @Test
  fun testPurgeCutoffIsExclusive() {
    val events =
      this.putEvents(
        listOf(this.base, this.base.plusSeconds(1L), this.base.plusSeconds(2L))
      )

    val deleted =
      this.purgeEvents(this.base.plusSeconds(1L))

    assertEquals(1, deleted)
    assertEquals(events.subList(1, 3), this.selectRemainingEvents())
  }

  @Test
  fun testPurgeWithCutoffAfterAllEventsDeletesEverything() {
    this.putEvents(
      listOf(this.base, this.base.plusSeconds(1L), this.base.plusSeconds(2L))
    )

    val deleted =
      this.purgeEvents(this.base.plusSeconds(3L))

    assertEquals(3, deleted)
    assertEquals(0, this.selectRemainingEvents().size)
  }

  @Test
  fun testPurgeIsIdempotent() {
    this.putEvents(
      listOf(this.base, this.base.plusSeconds(1L), this.base.plusSeconds(2L))
    )

    assertEquals(1, this.purgeEvents(this.base.plusSeconds(1L)))
    assertEquals(0, this.purgeEvents(this.base.plusSeconds(1L)))
    assertEquals(2, this.selectRemainingEvents().size)
  }
}
