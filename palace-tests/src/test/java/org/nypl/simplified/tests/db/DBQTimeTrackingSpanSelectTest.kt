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
import java.util.UUID

/**
 * Tests for the time tracking span select query.
 *
 * The query selects the spans which were received before a given cutoff time. The cutoff is
 * exclusive: a span received exactly at the cutoff time is not selected. The order in which
 * the spans are returned is not specified.
 */
class DBQTimeTrackingSpanSelectTest {

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

  private fun makeSpan(received: OffsetDateTime): TimeTrackingReceivedSpan {
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

  private fun putSpan(span: TimeTrackingReceivedSpan) {
    this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
      transaction.commit()
    }
  }

  private fun putSpans(received: List<OffsetDateTime>): List<TimeTrackingReceivedSpan> {
    val spans =
      received.map { this.makeSpan(it) }

    for (span in spans) {
      this.putSpan(span)
    }

    return spans
  }

  private fun selectSpans(cutoff: OffsetDateTime): List<TimeTrackingReceivedSpan> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingSpanSelectType::class.java, cutoff)
    }
  }

  @Test
  fun testSelectFromEmptyTableReturnsEmptyList() {
    assertEquals(0, this.selectSpans(this.base.plusDays(1)).size)
  }

  @Test
  fun testSelectReturnsOnlySpansReceivedBeforeCutoff() {
    val spans =
      this.putSpans(
        listOf(this.base, this.base.plusSeconds(10L), this.base.plusSeconds(20L))
      )

    val selected =
      this.selectSpans(this.base.plusSeconds(15L))

    assertEquals(
      setOf(spans[0].id, spans[1].id),
      selected.map { it.id }.toSet()
    )
  }

  @Test
  fun testSelectCutoffIsExclusive() {
    val spans =
      this.putSpans(listOf(this.base, this.base.plusSeconds(10L)))

    val selected =
      this.selectSpans(this.base.plusSeconds(10L))

    assertEquals(setOf(spans[0].id), selected.map { it.id }.toSet())
  }

  @Test
  fun testSelectWithCutoffAtEarliestReceivedTimeReturnsEmptyList() {
    this.putSpans(listOf(this.base, this.base.plusSeconds(10L)))

    assertEquals(0, this.selectSpans(this.base).size)
  }

  @Test
  fun testSelectWithCutoffAfterAllSpansReturnsAllSpans() {
    val spans =
      this.putSpans(
        listOf(this.base, this.base.plusSeconds(10L), this.base.plusSeconds(20L))
      )

    val selected =
      this.selectSpans(this.base.plusSeconds(30L))

    assertEquals(
      spans.map { it.id }.toSet(),
      selected.map { it.id }.toSet()
    )
  }

  @Test
  fun testSelectReturnsAllSpansWithSameReceivedTime() {
    val spans =
      this.putSpans(listOf(this.base, this.base, this.base))

    val selected =
      this.selectSpans(this.base.plusSeconds(1L))

    assertEquals(
      spans.map { it.id }.toSet(),
      selected.map { it.id }.toSet()
    )
  }

  @Test
  fun testSelectPreservesAllFields() {
    val span =
      this.makeSpan(this.base)

    this.putSpan(span)

    val selected =
      this.selectSpans(this.base.plusSeconds(1L))

    assertEquals(listOf(span), selected)
  }
}
