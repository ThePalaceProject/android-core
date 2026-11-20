package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import io.reactivex.subjects.PublishSubject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.books.time.tracking.TimeTrackingAudit
import org.nypl.simplified.books.time.tracking.TimeTrackingCollector
import org.nypl.simplified.books.time.tracking.TimeTrackingCollectorServiceType
import org.nypl.simplified.books.time.tracking.api.TimeTrackingStatus
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockProfile
import org.nypl.simplified.tests.mocking.MockProfilesController
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

class TimeTrackingCollectorTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingCollectorTest::class.java)

  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val palaceIDWrong =
    PlayerPalaceID("e1cc010f-e88d-4c16-a12e-ab7b9f5c2065")

  private lateinit var database: DBType
  private lateinit var directory: Path
  private lateinit var profiles: MockProfilesController
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var collector: TimeTrackingCollectorServiceType
  private lateinit var timeSegments: PublishSubject<PlayerTimeTracked>
  private lateinit var status: AttributeType<TimeTrackingStatus>
  private lateinit var accountRef: MockAccount
  private lateinit var profileRef: MockProfile
  private lateinit var accountProviderRef: AccountProviderType
  private lateinit var diagnostics: DiagnosticsServiceType

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.directory = directory
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
    this.accountProviderRef =
      Mockito.mock(AccountProviderType::class.java)

    this.status =
      Attributes.create { _ -> }
        .withValue(TimeTrackingStatus.Inactive)

    this.timeSegments =
      PublishSubject.create()
    this.resources.add(AutoCloseable { this.timeSegments.onComplete() })

    this.diagnostics =
      Mockito.mock(DiagnosticsServiceType::class.java)

    val audit =
      TimeTrackingAudit.create(
        database = this.database,
        clock = { OffsetDateTime.now(ZoneOffset.UTC) },
        diagnostics = this.diagnostics,
      )

    this.collector =
      TimeTrackingCollector.create(
        database = this.database,
        profiles = this.profiles,
        status = this.status,
        timeSegments = this.timeSegments,
        audit = audit,
        clock = { OffsetDateTime.now(ZoneOffset.UTC) },
        diagnostics = this.diagnostics,
      )
    this.resources.add(AutoCloseable { this.collector.close() })
  }

  @AfterEach
  fun tearDown() {
    this.resources.close()
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorRecordsSpans() {
    this.status.set(
      TimeTrackingStatus.Active(
        accountID = this.accountRef.id,
        bookId = this.palaceID,
        libraryId = "1014c482-0629-4a57-87ae-f9cc6e933397",
        timeTrackingUri = URI.create("http://www.example.com"),
      ),
    )

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceID,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        rate = 1.0,
      ),
    )

    this.collector.awaitWrite(1L, TimeUnit.SECONDS)

    val spans =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(
            DBQTimeTrackingSpanSelectType::class.java,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
          )
        }
      }

    assertEquals(1, spans.size)
    val span = spans[0]
    assertEquals(OffsetDateTime.parse("2024-10-10T00:00:00Z"), span.timeStarted)
    assertEquals(OffsetDateTime.parse("2024-10-10T00:00:30Z"), span.timeEnded)
    assertEquals(UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"), span.id)
    assertEquals(this.palaceID, span.bookID)
    assertEquals(this.accountRef.id, span.accountID)
    assertEquals(URI.create("http://www.example.com"), span.targetURI)
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorRejectsNonPositiveDurationSpans() {
    this.status.set(
      TimeTrackingStatus.Active(
        accountID = this.accountRef.id,
        bookId = this.palaceID,
        libraryId = "1014c482-0629-4a57-87ae-f9cc6e933397",
        timeTrackingUri = URI.create("http://www.example.com"),
      ),
    )

    /*
     * A span whose end time lies before its start time (a corrupted player report) must not
     * be recorded; if it reached the merge it would violate the entry constraint and stall
     * the pipeline for every book and account.
     */

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceID,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        rate = 1.0,
      ),
    )

    /*
     * A span with zero duration carries no playback data and is likewise rejected.
     */

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df8"),
        bookTrackingId = this.palaceID,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:01:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:01:00Z"),
        rate = 1.0,
      ),
    )

    /*
     * Each processed segment offers a write signal; two segments were emitted, so await two.
     */

    this.collector.awaitWrite(1L, TimeUnit.SECONDS)
    this.collector.awaitWrite(1L, TimeUnit.SECONDS)

    val spans =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(
            DBQTimeTrackingSpanSelectType::class.java,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
          )
        }
      }

    assertTrue(spans.isEmpty())

    /*
     * Both spans must have been reported as losses, with the corrupted times in the payload.
     */

    Mockito.verify(this.diagnostics)
      .report(
        DiagnosticCodes.TIME_TRACKING_LOSS,
        mapOf(
          Pair("Reason", "nonPositiveDuration"),
          Pair("Book", "cbd92367-f3e1-4310-a767-a07058271c2b"),
          Pair("TimeStarted", "2024-10-10T00:00:30Z"),
          Pair("TimeEnded", "2024-10-10T00:00Z"),
        ),
        null,
      )
    Mockito.verify(this.diagnostics)
      .report(
        DiagnosticCodes.TIME_TRACKING_LOSS,
        mapOf(
          Pair("Reason", "nonPositiveDuration"),
          Pair("Book", "cbd92367-f3e1-4310-a767-a07058271c2b"),
          Pair("TimeStarted", "2024-10-10T00:01Z"),
          Pair("TimeEnded", "2024-10-10T00:01Z"),
        ),
        null,
      )
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorIgnoresInactiveSpans() {
    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceID,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        rate = 1.0,
      ),
    )

    Thread.sleep(1_000L)

    val spans =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(
            DBQTimeTrackingSpanSelectType::class.java,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
          )
        }
      }

    assertTrue(spans.isEmpty())
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorIgnoresSpansForWrongBook() {
    this.status.set(
      TimeTrackingStatus.Active(
        accountID = this.accountRef.id,
        bookId = this.palaceID,
        libraryId = "1014c482-0629-4a57-87ae-f9cc6e933397",
        timeTrackingUri = URI.create("http://www.example.com"),
      ),
    )

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceIDWrong,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        rate = 1.0,
      ),
    )

    Thread.sleep(1_000L)

    val spans =
      this.database.openConnection().use { connection ->
        connection.openTransaction().use { transaction ->
          transaction.execute(
            DBQTimeTrackingSpanSelectType::class.java,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
          )
        }
      }

    assertTrue(spans.isEmpty())
  }
}
