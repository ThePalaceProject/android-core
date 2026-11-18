package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import io.azam.ulidj.ULID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.TimeTrackingAudit
import org.nypl.simplified.books.time.tracking.TimeTrackingSender
import org.nypl.simplified.books.time.tracking.TimeTrackingSenderServiceType
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponse
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseSummary
import org.nypl.simplified.tests.mocking.FakeTimeTrackingHTTPCalls
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockProfile
import org.nypl.simplified.tests.mocking.MockProfilesController
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

class TimeTrackingSenderTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingSenderTest::class.java)

  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")

  private lateinit var database: DBType
  private lateinit var profiles: MockProfilesController
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var sender: TimeTrackingSenderServiceType
  private lateinit var httpCalls: FakeTimeTrackingHTTPCalls
  private lateinit var accountRef: MockAccount
  private lateinit var profileRef: MockProfile
  private lateinit var diagnostics: DiagnosticsServiceType

  @BeforeEach
  fun setup(@TempDir directory: Path) {
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

    this.profiles =
      MockProfilesController(1, 1)
    this.profileRef =
      this.profiles.profileList[0]
    this.accountRef =
      this.profileRef.accountList[0]

    this.diagnostics =
      Mockito.mock(DiagnosticsServiceType::class.java)

    this.httpCalls =
      FakeTimeTrackingHTTPCalls()

    val audit =
      TimeTrackingAudit.create(
        database = this.database,
        clock = { OffsetDateTime.now(ZoneOffset.UTC) },
        diagnostics = this.diagnostics,
      )

    this.sender =
      TimeTrackingSender.create(
        database = this.database,
        profiles = this.profiles,
        httpCalls = this.httpCalls,
        audit = audit,
        frequency = Duration.ofMillis(100L),
        diagnostics = this.diagnostics,
      )
    this.resources.add(AutoCloseable { this.sender.close() })
  }

  @AfterEach
  fun tearDown() {
    this.resources.close()
  }

  private fun makeEntry(accountID: AccountID): TimeTrackingEntryOutgoing {
    return TimeTrackingEntryOutgoing(
      accountID = accountID,
      bookID = this.palaceID,
      targetURI = URI.create("https://www.example.com"),
      libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
      timeEntry =
        TimeTrackingEntry(
          id = ULID.random(),
          duringMinute = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
          secondsPlayed = 30,
        ),
    )
  }

  private fun selectEntries(): List<TimeTrackingEntryOutgoing> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
      }
    }
  }

  private fun putEntry(entry: TimeTrackingEntryOutgoing) {
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntryPutType::class.java, entry)
        transaction.commit()
      }
    }
  }

  /**
   * Poll the database (yielding to the sender's tick thread on each iteration) until the given
   * condition is true, or the timeout elapses. Returns the final value of the condition.
   *
   * A single awaitWrite is not sufficient: the tick that it signals may have run before the test
   * inserted its data. Polling the observed state removes that race.
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
      this.sender.awaitWrite(50L, TimeUnit.MILLISECONDS)
    }
    return condition()
  }

  private fun responseFor(
    entryId: String,
    status: Int,
    message: String,
    successes: Int,
    failures: Int,
  ): TimeTrackingServerResponse {
    return TimeTrackingServerResponse(
      responses =
        listOf(
          TimeTrackingServerResponseEntry(
            id = entryId,
            message = message,
            status = status,
          ),
        ),
      summary =
        TimeTrackingServerResponseSummary(
          successes = successes,
          failures = failures,
          total = 1,
        ),
    )
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testSenderDeletesEntryOnSuccess() {
    val entry = this.makeEntry(this.accountRef.id)
    this.httpCalls.responses.add(this.responseFor(entry.timeEntry.id, 200, "ok", 1, 0))
    this.putEntry(entry)

    assertTrue(this.pollUntil(4000L) { this.selectEntries().isEmpty() })
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testSenderRetainsEntryOnFailure() {
    val entry = this.makeEntry(this.accountRef.id)
    this.httpCalls.responses.add(this.responseFor(entry.timeEntry.id, 503, "unavailable", 0, 1))
    this.putEntry(entry)

    assertTrue(this.pollUntil(4000L) { this.httpCalls.requests.isNotEmpty() })

    val entries = this.selectEntries()
    assertEquals(1, entries.size)
    assertEquals(entry.timeEntry.id, entries[0].timeEntry.id)
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testSenderRetainsEntryOnException() {
    val entry = this.makeEntry(this.accountRef.id)
    this.httpCalls.crashes.add(IOException("network down"))
    this.putEntry(entry)

    assertTrue(this.pollUntil(4000L) { this.httpCalls.requests.isNotEmpty() })

    val entries = this.selectEntries()
    assertEquals(1, entries.size)
    assertEquals(entry.timeEntry.id, entries[0].timeEntry.id)
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testSenderRetainsOrphanedEntries() {
    val orphanAccountID =
      AccountID(UUID.randomUUID())
    val entry = this.makeEntry(orphanAccountID)
    this.putEntry(entry)

    /*
     * The account does not exist in the profile, so the entry cannot be sent. Give the sender a
     * few ticks to (fail to) process it, then confirm that it was neither sent nor deleted.
     */

    this.sender.awaitWrite(600L, TimeUnit.MILLISECONDS)
    this.sender.awaitWrite(600L, TimeUnit.MILLISECONDS)

    assertTrue(this.httpCalls.requests.isEmpty())

    val entries = this.selectEntries()
    assertEquals(1, entries.size)
    assertEquals(entry.timeEntry.id, entries[0].timeEntry.id)
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testSenderDeletesEntryOnPermanentFailure() {
    val entry = this.makeEntry(this.accountRef.id)
    this.httpCalls.responses.add(this.responseFor(entry.timeEntry.id, 410, "gone", 0, 1))
    this.putEntry(entry)

    assertTrue(this.pollUntil(4000L) { this.selectEntries().isEmpty() })
  }
}
