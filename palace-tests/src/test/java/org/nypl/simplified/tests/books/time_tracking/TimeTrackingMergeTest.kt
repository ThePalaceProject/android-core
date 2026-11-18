package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEventOperation
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.TimeTrackingMerge
import org.nypl.simplified.books.time.tracking.TimeTrackingMergeServiceType
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

class TimeTrackingMergeTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingMergeTest::class.java)

  private val accountID =
    AccountID(UUID.randomUUID())
  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val targetURI =
    URI.create("https://www.example.com")

  @Volatile
  private lateinit var timeNow: OffsetDateTime
  private lateinit var clock: () -> OffsetDateTime
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var merge: TimeTrackingMergeServiceType
  private lateinit var database: DBType
  private lateinit var diagnostics: DiagnosticsServiceType

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.timeNow = OffsetDateTime.now(ZoneOffset.UTC)
    this.clock = { this.timeNow }

    this.resources = CloseableCollection.create()

    val parsers =
      AccountProviderDescriptionCollectionParsers()
    val serializers =
      AccountProviderDescriptionCollectionSerializers()
    this.database =
      DBFactory.open(
        DBParameters(
          file = directory.resolve("database.db"),
          accountProviderParsers = parsers,
          accountProviderSerializers = serializers,
        ),
      )
    this.resources.add(AutoCloseable { this.database.close() })

    this.diagnostics =
      Mockito.mock(DiagnosticsServiceType::class.java)

    this.merge =
      TimeTrackingMerge.create(
        database = this.database,
        clock = this.clock,
        frequency = Duration.ofMillis(100L),
        diagnostics = this.diagnostics,
      )
    this.resources.add(AutoCloseable { this.merge.close() })
  }

  @AfterEach
  fun tearDown() {
    this.resources.close()
  }

  private fun makeSpan(
    started: OffsetDateTime,
    ended: OffsetDateTime,
  ): TimeTrackingReceivedSpan {
    return TimeTrackingReceivedSpan(
      id = UUID.randomUUID(),
      accountID = this.accountID,
      bookID = this.palaceID,
      libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
      timeStarted = started,
      timeEnded = ended,
      timeReceived = this.timeNow.minusMinutes(5),
      targetURI = this.targetURI,
    )
  }

  private fun selectEntries(): List<TimeTrackingEntryOutgoing> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
      }
    }
  }

  private fun selectSpans(): List<TimeTrackingReceivedSpan> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(
          DBQTimeTrackingSpanSelectType::class.java,
          this.timeNow.plusDays(1),
        )
      }
    }
  }

  /**
   * Poll the database (yielding to the merge's tick thread on each iteration) until the given
   * condition is true, or the timeout elapses. Returns the final value of the condition.
   */

  private fun pollUntil(
    timeoutMillis: Long,
    condition: () -> Boolean,
  ): Boolean {
    val deadline =
      System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
      if (condition()) {
        return true
      }
      this.merge.awaitTick(50L, TimeUnit.MILLISECONDS)
    }
    return condition()
  }

  @Test
  fun testMergeOneMinuteBoundary() {
    val spans =
      listOf(
        this.makeSpan(
          started = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
          ended = OffsetDateTime.parse("2024-10-15T00:01:30Z"),
        ),
      )

    /*
     * An entry that spans a minute boundary produces two time tracking entries.
     */

    val totalExpected = 60

    val entries =
      TimeTrackingMerge.mergeEntries(spans)
    val totalReceived =
      entries.sumOf { e -> e.timeEntry.secondsPlayed }

    assertEquals(totalExpected, totalReceived)
    assertEquals(2, entries.size)
  }

  @Test
  fun testMergeMultiMinuteSpan() {
    val spans =
      listOf(
        this.makeSpan(
          started = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
          ended = OffsetDateTime.parse("2024-10-15T00:02:30Z"),
        ),
      )

    /*
     * A single span that crosses two minute boundaries must produce one entry per minute
     * it touches: thirty seconds in the first minute, sixty in the middle minute, and
     * thirty in the final minute.
     */

    val entries =
      TimeTrackingMerge.mergeEntries(spans)

    assertEquals(3, entries.size)

    val seconds =
      entries
        .sortedBy { e -> e.timeEntry.duringMinute }
        .map { e -> e.timeEntry.secondsPlayed }
    assertEquals(listOf(30, 60, 30), seconds)
    assertEquals(120, entries.sumOf { e -> e.timeEntry.secondsPlayed })
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testMergeQuarantinesInvalidSpans() {
    val span =
      this.makeSpan(
        started = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        ended = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
      )

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
        transaction.commit()
      }
    }

    /*
     * A span whose end time lies before its start time carries no usable data. It must be
     * removed from the table so that it cannot violate the entry constraint and stall the
     * pipeline on every tick, and its presence must be reported as a loss. No entry may be
     * produced from it.
     */

    assertTrue(this.pollUntil(4000L) { this.selectSpans().isEmpty() })

    val entries =
      this.selectEntries()
    assertTrue(entries.isEmpty())

    Mockito.verify(this.diagnostics)
      .report(
        DiagnosticCodes.TIME_TRACKING_LOSS,
        mapOf(
          Pair("Reason", "invalidSpanDuration"),
          Pair("Book", "cbd92367-f3e1-4310-a767-a07058271c2b"),
          Pair("TimeStarted", "2024-10-15T00:00:30Z"),
          Pair("TimeEnded", "2024-10-15T00:00Z"),
        ),
        null,
      )
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testMergeInvalidSpanDoesNotBlockValidSpan() {
    val invalid =
      this.makeSpan(
        started = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        ended = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
      )
    val valid =
      this.makeSpan(
        started = OffsetDateTime.parse("2024-10-15T00:01:00Z"),
        ended = OffsetDateTime.parse("2024-10-15T00:01:30Z"),
      )

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, invalid)
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, valid)
        transaction.commit()
      }
    }

    /*
     * The invalid span and the valid span share a merge key. The invalid span must not roll
     * back the merge of the valid span; the valid span must produce its entry, and both
     * spans must be removed from the table.
     */

    assertTrue(this.pollUntil(4000L) { this.selectEntries().isNotEmpty() })

    val entries =
      this.selectEntries()
    val spans =
      this.selectSpans()

    assertEquals(1, entries.size)
    assertEquals(30, entries[0].timeEntry.secondsPlayed)
    assertEquals(this.accountID, entries[0].accountID)
    assertEquals(this.palaceID, entries[0].bookID)
    assertTrue(spans.isEmpty())

    Mockito.verify(this.diagnostics)
      .report(
        DiagnosticCodes.TIME_TRACKING_LOSS,
        mapOf(
          Pair("Reason", "invalidSpanDuration"),
          Pair("Book", "cbd92367-f3e1-4310-a767-a07058271c2b"),
          Pair("TimeStarted", "2024-10-15T00:00:30Z"),
          Pair("TimeEnded", "2024-10-15T00:00Z"),
        ),
        null,
      )
  }

  @Test
  fun testMergeOverlapping() {
    val spans =
      listOf(
        this.makeSpan(
          started = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
          ended = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        ),
        this.makeSpan(
          started = OffsetDateTime.parse("2024-10-15T00:00:10Z"),
          ended = OffsetDateTime.parse("2024-10-15T00:00:50Z"),
        ),
      )

    /*
     * The two spans overlap, and the total time played is 80 seconds. The merge must
     * not report more than 60 seconds for the minute.
     */

    val entries =
      TimeTrackingMerge.mergeEntries(spans)
    assertEquals(1, entries.size)
    assertEquals(60, entries[0].timeEntry.secondsPlayed)
  }

  @Test
  fun testMergeSeparateMinutes() {
    val spans =
      listOf(
        this.makeSpan(
          started = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
          ended = OffsetDateTime.parse("2024-10-15T00:00:20Z"),
        ),
        this.makeSpan(
          started = OffsetDateTime.parse("2024-10-15T00:01:00Z"),
          ended = OffsetDateTime.parse("2024-10-15T00:01:40Z"),
        ),
      )

    val entries =
      TimeTrackingMerge.mergeEntries(spans)
    assertEquals(2, entries.size)

    val seconds =
      entries
        .sortedBy { e -> e.timeEntry.duringMinute }
        .map { e -> e.timeEntry.secondsPlayed }
    assertEquals(listOf(20, 40), seconds)
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testMergeMovesSpansToEntries() {
    val span =
      this.makeSpan(
        started = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
        ended = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
      )

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
        transaction.commit()
      }
    }

    assertTrue(this.pollUntil(4000L) { this.selectEntries().isNotEmpty() })

    val entries = this.selectEntries()
    val spans = this.selectSpans()

    assertEquals(1, entries.size)
    assertEquals(30, entries[0].timeEntry.secondsPlayed)
    assertEquals(this.accountID, entries[0].accountID)
    assertEquals(this.palaceID, entries[0].bookID)
    assertTrue(spans.isEmpty())

    /*
     * The entry creation must also have been recorded in the event log, atomically with the
     * entry itself.
     */

    val events =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(
            DBQTimeTrackingEventSelectType::class.java,
            this.timeNow.minusDays(1),
          )
        }
      }

    assertEquals(1, events.size)
    val event = events[0]
    assertEquals(TimeTrackingEventOperation.TimeTrackingEntryCreated, event.operation)
    assertEquals("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b", event.libraryID)
    assertEquals("cbd92367-f3e1-4310-a767-a07058271c2b", event.bookID)
    assertEquals(entries[0].timeEntry.id, event.entryID)
    assertEquals(OffsetDateTime.parse("2024-10-15T00:00:00Z"), event.duringMinute)
    assertEquals(30, event.secondsPlayed)
    assertEquals(null, event.exception)
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testMergeDoesNotTouchRecentSpans() {
    val span =
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        timeReceived = this.timeNow,
        targetURI = this.targetURI,
      )

    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
        transaction.commit()
      }
    }

    /*
     * The span was just received, so it must not yet be merged. Let a few ticks elapse and
     * confirm that the span is retained and no entry was produced.
     */

    this.merge.awaitTick(600L, TimeUnit.MILLISECONDS)
    this.merge.awaitTick(600L, TimeUnit.MILLISECONDS)

    val spans = this.selectSpans()
    val entries = this.selectEntries()

    assertEquals(1, spans.size)
    assertTrue(entries.isEmpty())
  }
}
