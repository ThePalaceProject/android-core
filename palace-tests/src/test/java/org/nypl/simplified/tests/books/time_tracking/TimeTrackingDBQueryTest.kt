package org.nypl.simplified.tests.books.time_tracking

import io.azam.ulidj.ULID
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailed
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedExceptionally
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedPermanently
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptSucceeded
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStarted
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStopped
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPurgeType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests for the time tracking database queries that are not exercised at scale by the service
 * tests.
 *
 * The focus is on the query-level behaviors that the service tests do not pin down: the
 * round trip of every event type through the database, the select and purge time boundaries,
 * and the deletion of large batches of identifiers.
 */
class TimeTrackingDBQueryTest {
  private val count =
    1200

  private val accountID =
    AccountID(UUID.randomUUID())
  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val libraryID =
    URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b")
  private val targetURI =
    URI.create("https://www.example.com")
  private val timeNow =
    OffsetDateTime.now(ZoneOffset.UTC)

  @TempDir
  lateinit var directory: Path

  lateinit var database: DBType

  @BeforeEach
  fun setup() {
    val parsers =
      AccountProviderDescriptionCollectionParsers()
    val serializers =
      AccountProviderDescriptionCollectionSerializers()
    this.database =
      DBFactory.open(
        DBParameters(
          file = this.directory.resolve("database.db"),
          accountProviderParsers = parsers,
          accountProviderSerializers = serializers,
        ),
      )
  }

  private fun makeSpan(index: Int): TimeTrackingReceivedSpan {
    return TimeTrackingReceivedSpan(
      id = UUID.randomUUID(),
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      timeStarted = this.timeNow.minusMinutes(10).plusSeconds(index.toLong()),
      timeEnded = this.timeNow.minusMinutes(10).plusSeconds(index.toLong() + 30L),
      timeReceived = this.timeNow.minusMinutes(5),
      targetURI = this.targetURI,
    )
  }

  private fun makeEntry(index: Int): TimeTrackingEntryOutgoing {
    return TimeTrackingEntryOutgoing(
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      targetURI = this.targetURI,
      timeEntry =
        TimeTrackingEntry(
          id = ULID.random(),
          duringMinute = this.timeNow.minusMinutes(index.toLong()),
          secondsPlayed = 30,
        ),
    )
  }

  private fun putSpans(count: Int): List<String> {
    val spans =
      (0 until count).map { this.makeSpan(it) }

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        for (span in spans) {
          transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
        }
        transaction.commit()
      }
    }

    return spans.map { it.id.toString() }
  }

  private fun putEntries(count: Int): List<String> {
    val entries =
      (0 until count).map { this.makeEntry(it) }

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        for (entry in entries) {
          transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
        }
        transaction.commit()
      }
    }

    return entries.map { it.timeEntry.id }
  }

  private fun makeSpanWithReceivedTime(
    index: Int,
    received: OffsetDateTime,
  ): TimeTrackingReceivedSpan {
    return TimeTrackingReceivedSpan(
      id = UUID.randomUUID(),
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      timeStarted = received.minusMinutes(10L),
      timeEnded = received.minusMinutes(10L).plusSeconds(30L),
      timeReceived = received,
      targetURI = this.targetURI,
    )
  }

  private fun makeAllEvents(): List<TimeTrackingEvent> {
    val entryID =
      ULID.random()
    val base =
      OffsetDateTime.parse("2024-10-16T00:00:00Z")
    val libraryID =
      this.libraryID.toString()
    val bookID =
      this.palaceID.value

    return listOf(
        TimeTrackingStarted(
          time = base,
          libraryID = libraryID,
          bookID = bookID,
        ),
        TimeTrackingStopped(
          time = base.plusSeconds(1L),
          libraryID = libraryID,
          bookID = bookID,
        ),
        TimeTrackingEntryCreated(
          time = base.plusSeconds(2L),
          libraryID = libraryID,
          bookID = bookID,
          entryID = entryID,
          duringMinute = base.minusMinutes(1L),
          secondsPlayed = 30,
        ),
        TimeTrackingSendAttempt(
          time = base.plusSeconds(3L),
          libraryID = libraryID,
          bookID = bookID,
          entryID = entryID,
          secondsPlayed = 30,
        ),
        TimeTrackingSendAttemptSucceeded(
          time = base.plusSeconds(4L),
          libraryID = libraryID,
          bookID = bookID,
          entryID = entryID,
          secondsPlayed = 30,
        ),
        TimeTrackingSendAttemptFailed(
          time = base.plusSeconds(5L),
          libraryID = libraryID,
          bookID = bookID,
          entryID = entryID,
          secondsPlayed = 30,
        ),
        TimeTrackingSendAttemptFailedExceptionally(
          time = base.plusSeconds(6L),
          libraryID = libraryID,
          bookID = bookID,
          entryID = entryID,
          secondsPlayed = 30,
          exception = "java.lang.IllegalStateException: the connection was reset",
        ),
        TimeTrackingSendAttemptFailedPermanently(
          time = base.plusSeconds(7L),
          libraryID = libraryID,
          bookID = bookID,
          entryID = entryID,
          secondsPlayed = 30,
        ),
      )
  }

  private fun putEvents(events: List<TimeTrackingEvent>) {
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        for (event in events) {
          transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
        }
        transaction.commit()
      }
    }
  }

  private fun selectSpans(cutoff: OffsetDateTime): List<TimeTrackingReceivedSpan> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanSelectType::class.java, cutoff)
      }
    }
  }

  private fun selectSpanCount(): Int {
    return this.selectSpans(this.timeNow.plusDays(1)).size
  }

  private fun selectEvents(cutoff: OffsetDateTime): List<TimeTrackingEvent> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEventSelectType::class.java, cutoff)
      }
    }
  }

  private fun selectEntryCount(): Int {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        val entries =
          transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
        entries.size
      }
    }
  }

  @Test
  fun testSpanDeleteRemovesAllGivenIdentifiers() {
    val ids =
      this.putSpans(this.count)

    assertEquals(this.count, this.selectSpanCount())

    val deleted =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          val result =
            transaction.execute(DBQTimeTrackingSpanDeleteType::class.java, ids)
          transaction.commit()
          result
        }
      }

    assertEquals(this.count, deleted)
    assertEquals(0, this.selectSpanCount())
  }

  @Test
  fun testEntryDeleteRemovesAllGivenIdentifiers() {

    val ids =
      this.putEntries(this.count)

    assertEquals(this.count, this.selectEntryCount())

    val deleted =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          val result =
            transaction.execute(DBQTimeTrackingEntryDeleteType::class.java, ids)
          transaction.commit()
          result
        }
      }

    assertEquals(this.count, deleted)
    assertEquals(0, this.selectEntryCount())
  }

  @Test
  fun testEventPutSelectRoundTrip() {
    val events =
      this.makeAllEvents()

    this.putEvents(events)

    val selected =
      this.selectEvents(events[0].time.minusSeconds(1L))

    assertEquals(events, selected)
  }

  @Test
  fun testEventSelectCutoff() {
    val events =
      this.makeAllEvents()

    this.putEvents(events)

    /*
     * The cutoff is inclusive: an event recorded exactly at the cutoff time is selected.
     */

    assertEquals(events, this.selectEvents(events[0].time))
    assertEquals(events.subList(4, 8), this.selectEvents(events[4].time))
  }

  @Test
  fun testEventPurgeDeletesOldKeepsNew() {
    val events =
      this.makeAllEvents()

    this.putEvents(events)

    /*
     * The purge boundary is exclusive: an event recorded exactly at the cutoff time is
     * kept.
     */

    val deleted =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          val result =
            transaction.execute(DBQTimeTrackingEventPurgeType::class.java, events[4].time)
          transaction.commit()
          result
        }
      }

    assertEquals(4, deleted)
    assertEquals(events.subList(4, 8), this.selectEvents(events[0].time))
  }

  @Test
  fun testSpanSelectCutoff() {
    val base =
      OffsetDateTime.parse("2024-10-16T00:00:00Z")
    val spans =
      listOf(
        this.makeSpanWithReceivedTime(0, base),
        this.makeSpanWithReceivedTime(1, base.plusSeconds(10L)),
        this.makeSpanWithReceivedTime(2, base.plusSeconds(20L)),
      )

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        for (span in spans) {
          transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
        }
        transaction.commit()
      }
    }

    /*
     * The cutoff is exclusive: a span received exactly at the cutoff time is not selected.
     */

    assertEquals(listOf(spans[0]), this.selectSpans(base.plusSeconds(10L)))
    assertEquals(spans, this.selectSpans(base.plusSeconds(30L)))
  }
}
