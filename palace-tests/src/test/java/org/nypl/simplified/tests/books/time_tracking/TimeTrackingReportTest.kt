package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.TimeTrackingReport
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEventOperation
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TimeTrackingReportTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingReportTest::class.java)

  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var database: DBType

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.resources =
      CloseableCollection.create()

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
  }

  @AfterEach
  fun tearDown() {
    this.resources.close()
  }

  private fun putEvent(event: TimeTrackingEvent) {
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEventPutType::class.java, event)
        transaction.commit()
      }
    }
  }

  /**
   * The events CSV must have a header whose columns match the fields of each row. The rows
   * are emitted in chronological (sequence) order, and fields containing CSV metacharacters
   * must be quoted so that the rows round-trip through a standard CSV parser.
   */

  @Test
  fun testEventsCsvColumns() {
    val exceptionText =
      "failed, code 500 \"boom\""
    val event0 =
      TimeTrackingEvent(
        operation = TimeTrackingEventOperation.TimeTrackingEntryCreated,
        time = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
        libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
        entryID = "01J2A3B4C5D6E7F8A9B0C1D2E3",
        duringMinute = OffsetDateTime.parse("2024-10-15T00:00:00+05:30"),
        secondsPlayed = 42,
        exception = null,
      )
    val event1 =
      TimeTrackingEvent(
        operation = TimeTrackingEventOperation.TimeTrackingSendAttemptFailed,
        time = OffsetDateTime.parse("2024-10-15T00:01:00Z"),
        libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
        entryID = null,
        duringMinute = null,
        secondsPlayed = null,
        exception = exceptionText,
      )
    this.putEvent(event0)
    this.putEvent(event1)

    val csv =
      TimeTrackingReport.eventsCsv(
        database = this.database,
        since = OffsetDateTime.parse("2024-10-14T00:00:00Z"),
      )

    val rows =
      this.parseCsv(csv)

    assertEquals(
      listOf(
        "time",
        "operation",
        "library_id",
        "book_id",
        "entry_id",
        "during_minute",
        "seconds_played",
        "exception",
      ),
      rows[0],
    )

    /*
     * The first row must carry the values of the first-inserted event, with the original
     * offset of duringMinute preserved.
     */

    assertEquals(
      /*
       * Note the time rendering: OffsetDateTime.toString omits the seconds field when it is
       * zero, so the UTC event times render without seconds, and the duringMinute retains its
       * original offset.
       */

      listOf(
        "2024-10-15T00:00Z",
        "TimeTrackingEntryCreated",
        "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        "cbd92367-f3e1-4310-a767-a07058271c2b",
        "01J2A3B4C5D6E7F8A9B0C1D2E3",
        "2024-10-15T00:00+05:30",
        "42",
        "",
      ),
      rows[1],
    )

    /*
     * The second row must carry the values of the second-inserted event. The null fields
     * are rendered as empty strings, and the exception text, which contains CSV
     * metacharacters, must round-trip intact.
     */

    assertEquals(
      listOf(
        "2024-10-15T00:01Z",
        "TimeTrackingSendAttemptFailed",
        "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        "cbd92367-f3e1-4310-a767-a07058271c2b",
        "",
        "",
        "",
        exceptionText,
      ),
      rows[2],
    )

    assertEquals(3, rows.size)
  }

  @Test
  fun testEventsCsvEmpty() {
    val csv =
      TimeTrackingReport.eventsCsv(
        database = this.database,
        since = OffsetDateTime.parse("2024-10-14T00:00:00Z"),
      )

    val rows =
      this.parseCsv(csv)

    assertEquals(1, rows.size)
    assertEquals(
      listOf(
        "time",
        "operation",
        "library_id",
        "book_id",
        "entry_id",
        "during_minute",
        "seconds_played",
        "exception",
      ),
      rows[0],
    )
  }

  /**
   * Parse the given CSV text into rows of fields, honouring RFC 4180 quoting (fields may be
   * double-quoted; embedded quotes are doubled; embedded newlines are preserved).
   */

  private fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
      val c = text[i]
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < text.length && text[i + 1] == '"') {
            current.append('"')
            i += 2
            continue
          }
          inQuotes = false
          i++
          continue
        }
        current.append(c)
        i++
        continue
      }
      if (c == '"') {
        inQuotes = true
        i++
        continue
      }
      if (c == ',') {
        fields.add(current.toString())
        current.setLength(0)
        i++
        continue
      }
      if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
          i++
        }
        fields.add(current.toString())
        current.setLength(0)
        rows.add(fields.toList())
        fields.clear()
        i++
        continue
      }
      current.append(c)
      i++
    }
    if (current.isNotEmpty() || fields.isNotEmpty()) {
      fields.add(current.toString())
      rows.add(fields.toList())
    }
    return rows
  }
}
