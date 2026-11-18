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
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
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
 * The focus is the chunked deletion path: the delete statements split the identifier list into
 * chunks of 500 to stay within SQLite's bound-parameter limit, and the multi-chunk path is only
 * taken when more than 500 identifiers are deleted at once.
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

  private fun selectSpanCount(): Int {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        val spans =
          transaction.execute(
            DBQTimeTrackingSpanSelectType::class.java,
            this.timeNow.plusDays(1),
          )
        spans.size
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
  fun testSpanDeleteCrossesChunkBoundary() {
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
  fun testEntryDeleteCrossesChunkBoundary() {
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
}
