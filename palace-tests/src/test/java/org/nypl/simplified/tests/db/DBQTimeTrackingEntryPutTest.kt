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
 * Tests for the time tracking entry put query.
 *
 * The query inserts a single entry into the time tracking entries table. The table's primary
 * key is the entry identifier, and the insert is performed with the ignore conflict
 * resolution, so that an entry which has already been recorded is silently ignored rather
 * than raising an error or replacing the stored row. The query returns the number of rows
 * inserted.
 */
class DBQTimeTrackingEntryPutTest {

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

  private fun putEntry(entry: TimeTrackingEntryOutgoing): Int {
    return this.database.openTransaction().use { transaction ->
      val result =
        transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
      transaction.commit()
      result
    }
  }

  private fun selectAllEntries(): List<TimeTrackingEntryOutgoing> {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
    }
  }

  @Test
  fun testPutReturnsOneAndStoresAllFields() {
    val entry =
      this.makeEntry(ULID.random(), this.base.minusMinutes(1L), 30)

    val inserted =
      this.putEntry(entry)

    assertEquals(1, inserted)
    assertEquals(listOf(entry), this.selectAllEntries())
  }

  @Test
  fun testPutStoresMultipleEntries() {
    val entries =
      listOf(
        this.makeEntry(ULID.random(), this.base.minusMinutes(3L), 30),
        this.makeEntry(ULID.random(), this.base.minusMinutes(2L), 45),
        this.makeEntry(ULID.random(), this.base.minusMinutes(1L), 60),
      )

    for (entry in entries) {
      assertEquals(1, this.putEntry(entry))
    }

    assertEquals(
      entries.map { it.timeEntry.id }.toSet(),
      this.selectAllEntries().map { it.timeEntry.id }.toSet()
    )
  }

  @Test
  fun testPutPreservesNonUtcMinuteOffset() {
    val offset =
      ZoneOffset.ofHoursMinutes(5, 30)
    val duringMinute =
      this.base.minusMinutes(1L).withOffsetSameInstant(offset)
    val entry =
      this.makeEntry(ULID.random(), duringMinute, 30)

    this.putEntry(entry)

    assertEquals(listOf(entry), this.selectAllEntries())
  }

  @Test
  fun testPutOfAnAlreadyStoredEntryIsIgnored() {
    val original =
      this.makeEntry(ULID.random(), this.base.minusMinutes(1L), 30)
    val replacement =
      this.makeEntry(original.timeEntry.id, this.base, 60)

    assertEquals(1, this.putEntry(original))
    assertEquals(0, this.putEntry(replacement))

    /*
     * The ignore conflict resolution must not replace the stored row.
     */

    assertEquals(listOf(original), this.selectAllEntries())
  }
}
