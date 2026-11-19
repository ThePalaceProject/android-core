package org.nypl.simplified.tests.db

import io.azam.ulidj.ULID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests for the time tracking entry delete query.
 *
 * The query deletes the entries with the given identifiers, one statement per identifier,
 * and returns the total number of rows deleted. Identifiers which do not match a stored
 * entry delete nothing, and deleting an identifier which has already been deleted deletes
 * nothing again.
 */
class DBQTimeTrackingEntryDeleteTest {

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

  private fun makeEntry(index: Int): TimeTrackingEntryOutgoing {
    return TimeTrackingEntryOutgoing(
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      targetURI = this.targetURI,
      timeEntry =
        TimeTrackingEntry(
          id = ULID.random(),
          duringMinute = this.base.minusMinutes(index.toLong()),
          secondsPlayed = 30,
        ),
    )
  }

  private fun putEntries(count: Int): List<TimeTrackingEntryOutgoing> {
    val entries =
      (0 until count).map { this.makeEntry(it) }

    this.database.openTransaction().use { transaction ->
      for (entry in entries) {
        transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
      }
      transaction.commit()
    }

    return entries
  }

  private fun deleteEntries(ids: List<String>): Int {
    return this.database.openTransaction().use { transaction ->
      val result =
        transaction.execute(DBQTimeTrackingEntryDeleteType::class.java, ids)
      transaction.commit()
      result
    }
  }

  private fun selectEntryIds(): Set<String> {
    return this.database.openTransaction().use { transaction ->
      val entries =
        transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
      entries.map { it.timeEntry.id }.toSet()
    }
  }

  @Test
  fun testDeleteOfEmptyListDeletesNothing() {
    this.putEntries(3)

    assertEquals(0, this.deleteEntries(listOf()))
    assertEquals(3, this.selectEntryIds().size)
  }

  @Test
  fun testDeleteFromEmptyTableDeletesNothing() {
    assertEquals(0, this.deleteEntries(listOf(ULID.random())))
  }

  @Test
  fun testDeleteOfUnknownIdentifiersDeletesNothing() {
    val entries =
      this.putEntries(3)

    val deleted =
      this.deleteEntries(listOf(ULID.random(), ULID.random()))

    assertEquals(0, deleted)
    assertEquals(
      entries.map { it.timeEntry.id }.toSet(),
      this.selectEntryIds()
    )
  }

  @Test
  fun testDeleteRemovesOnlyTheGivenIdentifiers() {
    val entries =
      this.putEntries(3)

    val deleted =
      this.deleteEntries(listOf(entries[1].timeEntry.id))

    assertEquals(1, deleted)
    assertEquals(
      setOf(entries[0].timeEntry.id, entries[2].timeEntry.id),
      this.selectEntryIds()
    )
  }

  @Test
  fun testDeleteOfAllIdentifiersDeletesAllEntries() {
    val entries =
      this.putEntries(3)

    val deleted =
      this.deleteEntries(entries.map { it.timeEntry.id })

    assertEquals(3, deleted)
    assertEquals(0, this.selectEntryIds().size)
  }

  @Test
  fun testDeleteIsIdempotent() {
    val entries =
      this.putEntries(2)
    val ids =
      entries.map { it.timeEntry.id }

    assertEquals(2, this.deleteEntries(ids))
    assertEquals(0, this.deleteEntries(ids))
    assertEquals(0, this.selectEntryIds().size)
  }

  @Test
  fun testDeleteCountsEachOccurrenceOfAnIdentifier() {
    val entries =
      this.putEntries(1)
    val id =
      entries[0].timeEntry.id

    assertEquals(1, this.deleteEntries(listOf(id, id)))
    assertEquals(0, this.selectEntryIds().size)
  }

  @Test
  fun testDeleteRemovesAllGivenIdentifiersInLargeBatch() {
    val entries =
      this.putEntries(1200)

    val deleted =
      this.deleteEntries(entries.map { it.timeEntry.id })

    assertEquals(1200, deleted)
    assertEquals(0, this.selectEntryIds().size)
  }
}
