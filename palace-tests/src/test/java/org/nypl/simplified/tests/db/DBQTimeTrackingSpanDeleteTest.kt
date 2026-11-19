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
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests for the time tracking span delete query.
 *
 * The query deletes the spans with the given identifiers, one statement per identifier, and
 * returns the total number of rows deleted. Identifiers which do not match a stored span
 * delete nothing, and deleting an identifier which has already been deleted deletes nothing
 * again.
 */
class DBQTimeTrackingSpanDeleteTest {

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

  private fun makeSpan(index: Int): TimeTrackingReceivedSpan {
    return TimeTrackingReceivedSpan(
      id = UUID.randomUUID(),
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      timeStarted = this.base.plusSeconds(index.toLong()),
      timeEnded = this.base.plusSeconds(index.toLong() + 30L),
      timeReceived = this.base,
      targetURI = this.targetURI,
    )
  }

  private fun putSpans(count: Int): List<TimeTrackingReceivedSpan> {
    val spans =
      (0 until count).map { this.makeSpan(it) }

    this.database.openTransaction().use { transaction ->
      for (span in spans) {
        transaction.execute(DBQTimeTrackingSpanPutType::class.java, span)
      }
      transaction.commit()
    }

    return spans
  }

  private fun deleteSpans(ids: List<String>): Int {
    return this.database.openTransaction().use { transaction ->
      val result =
        transaction.execute(DBQTimeTrackingSpanDeleteType::class.java, ids)
      transaction.commit()
      result
    }
  }

  private fun selectSpanIds(): Set<String> {
    return this.database.openTransaction().use { transaction ->
      val spans =
        transaction.execute(DBQTimeTrackingSpanSelectType::class.java, this.base.plusDays(1))
      spans.map { it.id.toString() }.toSet()
    }
  }

  @Test
  fun testDeleteOfEmptyListDeletesNothing() {
    this.putSpans(3)

    assertEquals(0, this.deleteSpans(listOf()))
    assertEquals(3, this.selectSpanIds().size)
  }

  @Test
  fun testDeleteFromEmptyTableDeletesNothing() {
    assertEquals(
      0,
      this.deleteSpans(listOf(UUID.randomUUID().toString()))
    )
  }

  @Test
  fun testDeleteOfUnknownIdentifiersDeletesNothing() {
    val spans =
      this.putSpans(3)

    val deleted =
      this.deleteSpans(
        listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
      )

    assertEquals(0, deleted)
    assertEquals(
      spans.map { it.id.toString() }.toSet(),
      this.selectSpanIds()
    )
  }

  @Test
  fun testDeleteRemovesOnlyTheGivenIdentifiers() {
    val spans =
      this.putSpans(3)

    val deleted =
      this.deleteSpans(listOf(spans[1].id.toString()))

    assertEquals(1, deleted)
    assertEquals(
      setOf(spans[0].id.toString(), spans[2].id.toString()),
      this.selectSpanIds()
    )
  }

  @Test
  fun testDeleteOfAllIdentifiersDeletesAllSpans() {
    val spans =
      this.putSpans(3)

    val deleted =
      this.deleteSpans(spans.map { it.id.toString() })

    assertEquals(3, deleted)
    assertEquals(0, this.selectSpanIds().size)
  }

  @Test
  fun testDeleteIsIdempotent() {
    val spans =
      this.putSpans(2)
    val ids =
      spans.map { it.id.toString() }

    assertEquals(2, this.deleteSpans(ids))
    assertEquals(0, this.deleteSpans(ids))
    assertEquals(0, this.selectSpanIds().size)
  }

  @Test
  fun testDeleteCountsEachOccurrenceOfAnIdentifier() {
    val spans =
      this.putSpans(1)
    val id =
      spans[0].id.toString()

    assertEquals(1, this.deleteSpans(listOf(id, id)))
    assertEquals(0, this.selectSpanIds().size)
  }

  @Test
  fun testDeleteRemovesAllGivenIdentifiersInLargeBatch() {
    val spans =
      this.putSpans(1200)

    val deleted =
      this.deleteSpans(spans.map { it.id.toString() })

    assertEquals(1200, deleted)
    assertEquals(0, this.selectSpanIds().size)
  }
}
