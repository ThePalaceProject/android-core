package org.nypl.simplified.tests.db

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
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
 * Tests for the WAL SQLITE_BUSY_SNAPSHOT behaviour that affects the time tracking merge service.
 *
 * In WAL mode, a deferred transaction that reads and then writes can fail with
 * SQLITE_BUSY_SNAPSHOT if another connection commits a write in between the read and the write.
 * Unlike the plain SQLITE_BUSY error, SQLite does not invoke the busy handler for
 * SQLITE_BUSY_SNAPSHOT, so a configured busy timeout cannot rescue the transaction.
 *
 * The first test reproduces the failure deterministically. The second test shows that keeping
 * the read and the write in separate transactions, with the write transaction issuing a write as
 * its first statement, avoids the failure: the write transaction can only ever see the plain
 * SQLITE_BUSY error, which the busy handler retries.
 */
class DBTimeTrackingBusySnapshotTest {
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

  private fun selectCount(cutoff: OffsetDateTime): Int {
    return this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingSpanSelectType::class.java, cutoff).size
    }
  }

  @Test
  fun testReadThenWriteInSingleTransactionFailsWithBusySnapshot() {
    /*
     * Seed a span so that the read phase has data to snapshot.
     */

    this.putSpan(this.makeSpan(this.base))

    val connectionA =
      this.database.openConnection()
    val transactionA =
      connectionA.openTransaction()
    try {
      /*
       * Read phase: establishes the WAL read snapshot for this (deferred) transaction.
       */

      val readBack =
        transactionA.execute(DBQTimeTrackingSpanSelectType::class.java, this.base.plusDays(1))
      assertEquals(1, readBack.size)

      /*
       * Another connection commits a write after the snapshot was taken.
       */

      this.putSpan(this.makeSpan(this.base.plusSeconds(5L)))

      /*
       * Write phase: the same transaction now tries to upgrade from a read transaction to a
       * write transaction. Because another connection committed after its snapshot, this fails
       * with SQLITE_BUSY_SNAPSHOT, and the busy handler is not invoked.
       */

      val thrown =
        assertThrows(SQLiteException::class.java) {
          transactionA.execute(DBQTimeTrackingSpanPutType::class.java, this.makeSpan(this.base.plusSeconds(10L)))
        }
      assertEquals(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT, thrown.resultCode)
    } finally {
      transactionA.rollback()
      connectionA.close()
    }
  }

  @Test
  fun testWriteFirstTransactionSucceedsAfterIntermediateCommit() {
    /*
     * Seed a span so that the read phase has data to snapshot.
     */

    this.putSpan(this.makeSpan(this.base))

    /*
     * Phase 1 (read): a read-only transaction, closed before the write phase.
     */

    val readBack =
      this.database.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanSelectType::class.java, this.base.plusDays(1))
      }
    assertEquals(1, readBack.size)

    /*
     * Another connection commits a write after the read transaction has closed.
     */

    this.putSpan(this.makeSpan(this.base.plusSeconds(5L)))

    /*
     * Phase 2 (write): a separate transaction whose first statement is a write. Because the
     * transaction has no read snapshot, it can only ever see the plain SQLITE_BUSY error (which
     * the busy handler retries), never SQLITE_BUSY_SNAPSHOT.
     */

    val newSpan =
      this.makeSpan(this.base.plusSeconds(10L))
    this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingSpanPutType::class.java, newSpan)
      transaction.commit()
    }

    /*
     * All three spans are present: the seed, the intermediate commit, and the write-first insert.
     */

    assertEquals(3, this.selectCount(this.base.plusDays(1)))

    /*
     * The write-first transaction also performs a delete, confirming that writes and deletes in
     * the same transaction commit together.
     */

    this.database.openTransaction().use { transaction ->
      transaction.execute(DBQTimeTrackingSpanDeleteType::class.java, listOf(newSpan.id.toString()))
      transaction.commit()
    }
    assertEquals(2, this.selectCount(this.base.plusDays(1)))
  }
}
