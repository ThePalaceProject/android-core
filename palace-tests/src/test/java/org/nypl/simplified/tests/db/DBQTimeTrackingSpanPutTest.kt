package org.nypl.simplified.tests.db

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests for the time tracking span put query.
 *
 * The query inserts a single span into the time tracking spans table. The table's primary key
 * is the span identifier, and the insert is performed with the ignore conflict resolution, so
 * that a span which has already been recorded is silently ignored rather than raising an
 * error. The query returns the number of rows inserted.
 */
class DBQTimeTrackingSpanPutTest {

  private val accountID =
    AccountID(UUID.fromString("0c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f"))
  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val libraryID =
    URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b")
  private val targetURI =
    URI.create("https://www.example.com")
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

  private fun makeSpan(
    id: UUID,
    started: OffsetDateTime,
    ended: OffsetDateTime,
    received: OffsetDateTime,
  ): TimeTrackingReceivedSpan {
    return TimeTrackingReceivedSpan(
      id = id,
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      timeStarted = started,
      timeEnded = ended,
      timeReceived = received,
      targetURI = this.targetURI,
    )
  }

  private fun putSpan(span: TimeTrackingReceivedSpan): Int {
    return this.database.openTransaction().use { transaction ->
      val result =
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
      transaction.commit()
      result
    }
  }

  private fun selectSpans(cutoff: OffsetDateTime): List<TimeTrackingReceivedSpan> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingSpanSelectType::class.java, cutoff)
    }
  }

  private fun selectAllSpans(): List<TimeTrackingReceivedSpan> {
    return this.selectSpans(this.base.plusDays(1))
  }

  @Test
  fun testPutReturnsOneAndStoresAllFields() {
    val span =
      this.makeSpan(UUID.randomUUID(), this.base, this.base.plusSeconds(30L), this.base)

    val inserted =
      this.putSpan(span)

    assertEquals(1, inserted)
    assertEquals(listOf(span), this.selectAllSpans())
  }

  @Test
  fun testPutStoresMultipleSpans() {
    val spans =
      listOf(
        this.makeSpan(UUID.randomUUID(), this.base, this.base.plusSeconds(30L), this.base),
        this.makeSpan(
          UUID.randomUUID(),
          this.base.plusMinutes(1L),
          this.base.plusMinutes(1L).plusSeconds(30L),
          this.base.plusMinutes(1L),
        ),
        this.makeSpan(
          UUID.randomUUID(),
          this.base.plusMinutes(2L),
          this.base.plusMinutes(2L).plusSeconds(30L),
          this.base.plusMinutes(2L),
        ),
      )

    for (span in spans) {
      assertEquals(1, this.putSpan(span))
    }

    assertEquals(spans.toSet(), this.selectAllSpans().toSet())
  }

  @Test
  fun testPutPreservesNonUtcOffsets() {
    val offset =
      ZoneOffset.ofHoursMinutes(5, 30)
    val started =
      this.base.withOffsetSameInstant(offset).withNano(123456789)
    val ended =
      this.base.plusSeconds(30L).withOffsetSameInstant(offset)
    val span =
      this.makeSpan(UUID.randomUUID(), started, ended, this.base)

    this.putSpan(span)

    assertEquals(listOf(span), this.selectAllSpans())
  }

  @Test
  fun testPutOfAnAlreadyStoredSpanIsIgnored() {
    val span =
      this.makeSpan(UUID.randomUUID(), this.base, this.base.plusSeconds(30L), this.base)

    assertEquals(1, this.putSpan(span))
    assertEquals(0, this.putSpan(span))
    assertEquals(listOf(span), this.selectAllSpans())
  }
}
