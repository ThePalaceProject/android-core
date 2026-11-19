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
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests for the time tracking entry select query.
 *
 * The query selects all entries stored in the time tracking entries table. The order in
 * which the entries are returned is not specified.
 */
class DBQTimeTrackingEntrySelectTest {

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

  private fun makeEntry(
    id: String,
    duringMinute: OffsetDateTime,
    secondsPlayed: Int,
  ): TimeTrackingEntryOutgoing {
    return TimeTrackingEntryOutgoing(
      accountID = this.accountID,
      libraryID = this.libraryID,
      bookID = this.palaceID,
      targetURI = this.targetURI,
      timeEntry =
        TimeTrackingEntry(
          id = id,
          duringMinute = duringMinute,
          secondsPlayed = secondsPlayed,
        ),
    )
  }

  private fun putEntry(entry: TimeTrackingEntryOutgoing) {
    this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
      transaction.commit()
    }
  }

  private fun selectAllEntries(): List<TimeTrackingEntryOutgoing> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
    }
  }

  @Test
  fun testSelectFromEmptyTableReturnsEmptyList() {
    assertEquals(0, this.selectAllEntries().size)
  }

  @Test
  fun testSelectReturnsAllStoredEntries() {
    val entries =
      listOf(
        this.makeEntry(ULID.random(), this.base.minusMinutes(3L), 30),
        this.makeEntry(ULID.random(), this.base.minusMinutes(2L), 45),
        this.makeEntry(ULID.random(), this.base.minusMinutes(1L), 60),
      )

    for (entry in entries) {
      this.putEntry(entry)
    }

    assertEquals(
      entries.map { it.timeEntry.id }.toSet(),
      this.selectAllEntries().map { it.timeEntry.id }.toSet()
    )
  }

  @Test
  fun testSelectPreservesAllFields() {
    val offset =
      ZoneOffset.ofHoursMinutes(5, 30)
    val entry =
      this.makeEntry(
        ULID.random(),
        this.base.minusMinutes(1L).withOffsetSameInstant(offset),
        59,
      )

    this.putEntry(entry)

    assertEquals(listOf(entry), this.selectAllEntries())
  }
}
