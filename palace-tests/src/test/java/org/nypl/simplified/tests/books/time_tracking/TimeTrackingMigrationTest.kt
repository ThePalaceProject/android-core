package org.nypl.simplified.tests.books.time_tracking

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.books.time.tracking.TimeTrackingMigration
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.Properties

class TimeTrackingMigrationTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingMigrationTest::class.java)

  private lateinit var database: DBType
  private lateinit var directory: Path
  private lateinit var legacyDirectory: Path
  private lateinit var diagnostics: DiagnosticsServiceType

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.directory = directory

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

    this.legacyDirectory =
      directory.resolve("time_tracking")

    this.diagnostics =
      Mockito.mock(DiagnosticsServiceType::class.java)
  }

  @AfterEach
  fun tearDown() {
    this.database.close()
  }

  private fun writeLegacySpanFile(
    id: String,
    accountID: String,
    bookID: String,
    libraryID: String,
    targetURI: String,
    timeStarted: String,
    timeEnded: String,
  ): Path {
    val collectorDirectory =
      this.legacyDirectory.resolve("collector")
    Files.createDirectories(collectorDirectory)

    val p = Properties()
    p.setProperty("@Type", "TimeTrackingReceivedSpan")
    p.setProperty("@Version", "1")
    p.setProperty("ID", id)
    p.setProperty("AccountID", accountID)
    p.setProperty("BookID", bookID)
    p.setProperty("LibraryID", libraryID)
    p.setProperty("TargetURI", targetURI)
    p.setProperty("TimeEnded", timeEnded)
    p.setProperty("TimeStarted", timeStarted)

    val file =
      collectorDirectory.resolve("$id.ttspan")
    ByteArrayOutputStream().use { s ->
      p.store(s, "")
      Files.write(file, s.toByteArray())
    }
    return file
  }

  private fun writeLegacyEntryFile(
    id: String,
    accountID: String,
    bookID: String,
    libraryID: String,
    targetURI: String,
    minute: String,
    seconds: Int,
  ): Path {
    val senderDirectory =
      this.legacyDirectory.resolve("sender")
    Files.createDirectories(senderDirectory)

    val p = Properties()
    p.setProperty("@Type", "TimeTrackingEntryOutgoing")
    p.setProperty("@Version", "1")
    p.setProperty("ID", id)
    p.setProperty("AccountID", accountID)
    p.setProperty("BookID", bookID)
    p.setProperty("LibraryID", libraryID)
    p.setProperty("TargetURI", targetURI)
    p.setProperty("Minute", minute)
    p.setProperty("Seconds", seconds.toString())

    val file =
      senderDirectory.resolve("$id.tteo")
    ByteArrayOutputStream().use { s ->
      p.store(s, "")
      Files.write(file, s.toByteArray())
    }
    return file
  }

  private fun writeLegacyAuditRecord(properties: Properties) {
    val debugDirectory =
      this.legacyDirectory.resolve("debug")
    Files.createDirectories(debugDirectory)

    val file =
      debugDirectory.resolve("time_tracking_debug.dat")
    val s = ByteArrayOutputStream()
    properties.storeToXML(s, "")
    s.write("\n".toByteArray())
    s.write(byteArrayOf(0))
    if (Files.exists(file)) {
      Files.write(file, s.toByteArray(), java.nio.file.StandardOpenOption.APPEND)
    } else {
      Files.write(file, s.toByteArray())
    }
  }

  private fun writeLegacyAuditTail(text: String) {
    val debugDirectory =
      this.legacyDirectory.resolve("debug")
    Files.createDirectories(debugDirectory)
    val file =
      debugDirectory.resolve("time_tracking_debug.dat")
    Files.write(file, text.toByteArray(), java.nio.file.StandardOpenOption.APPEND)
  }

  private fun selectSpans(): List<*> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(
          DBQTimeTrackingSpanSelectType::class.java,
          OffsetDateTime.parse("2999-01-01T00:00:00Z"),
        )
      }
    }
  }

  private fun selectEntries(): List<*> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
      }
    }
  }

  private fun selectEvents(): List<*> {
    return this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(
          DBQTimeTrackingEventSelectType::class.java,
          OffsetDateTime.parse("0001-01-01T00:00:00Z"),
        )
      }
    }
  }

  @Test
  fun testMigrationOfEmptyDirectory() {
    Files.createDirectories(this.legacyDirectory)
    Files.createDirectories(this.legacyDirectory.resolve("collector"))
    Files.createDirectories(this.legacyDirectory.resolve("sender"))
    Files.createDirectories(this.legacyDirectory.resolve("debug"))

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    assertTrue(this.selectSpans().isEmpty())
    assertTrue(this.selectEntries().isEmpty())
    assertTrue(this.selectEvents().isEmpty())
  }

  @Test
  fun testMigrationOfAbsentDirectory() {
    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    assertTrue(this.selectSpans().isEmpty())
    assertTrue(this.selectEntries().isEmpty())
    assertTrue(this.selectEvents().isEmpty())
  }

  @Test
  fun testMigrationMovesSpanFiles() {
    val spanFile =
      this.writeLegacySpanFile(
        id = "0af973ee-9a2a-4ac3-a330-d8baee101df7",
        accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
        bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
        libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        targetURI = "https://www.example.com",
        timeStarted = "2024-10-15T00:00:00Z",
        timeEnded = "2024-10-15T00:00:30Z",
      )

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    val spans = this.selectSpans()
    assertEquals(1, spans.size)
    assertTrue(!Files.exists(spanFile))
  }

  @Test
  fun testMigrationMovesEntryFiles() {
    val entryFile =
      this.writeLegacyEntryFile(
        id = "01J2A3B4C5D6E7F8A9B0C1D2E3",
        accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
        bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
        libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        targetURI = "https://www.example.com",
        minute = "2024-10-15T00:00:00Z",
        seconds = 30,
      )

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    val entries = this.selectEntries()
    assertEquals(1, entries.size)
    assertTrue(!Files.exists(entryFile))
  }

  @Test
  fun testMigrationMovesAuditFile() {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingStarted")
    p.setProperty("Time", "2024-10-15T00:00:00Z")
    p.setProperty("LibraryID", "1014c482-0629-4a57-87ae-f9cc6e933397")
    p.setProperty("BookID", "cbd92367-f3e1-4310-a767-a07058271c2b")
    this.writeLegacyAuditRecord(p)

    val auditFile =
      this.legacyDirectory.resolve("debug/time_tracking_debug.dat")

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    val events = this.selectEvents()
    assertEquals(1, events.size)
    assertTrue(!Files.exists(auditFile))
  }

  @Test
  fun testMigrationIsIdempotent() {
    this.writeLegacySpanFile(
      id = "0af973ee-9a2a-4ac3-a330-d8baee101df7",
      accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
      bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
      libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
      targetURI = "https://www.example.com",
      timeStarted = "2024-10-15T00:00:00Z",
      timeEnded = "2024-10-15T00:00:30Z",
    )
    this.writeLegacyEntryFile(
      id = "01J2A3B4C5D6E7F8A9B0C1D2E3",
      accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
      bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
      libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
      targetURI = "https://www.example.com",
      minute = "2024-10-15T00:00:00Z",
      seconds = 30,
    )

    val migration =
      TimeTrackingMigration.create(
        database = this.database,
        legacyDirectory = this.legacyDirectory,
        diagnostics = this.diagnostics,
      )

    migration.migrate()
    migration.migrate()

    assertEquals(1, this.selectSpans().size)
    assertEquals(1, this.selectEntries().size)
  }

  @Test
  fun testMigrationRetainsUnparseableSpanFile() {
    val badFile =
      this.legacyDirectory.resolve("collector/bad.ttspan")
    Files.createDirectories(this.legacyDirectory.resolve("collector"))
    Files.write(badFile, "not a properties file at all".toByteArray())

    val goodFile =
      this.writeLegacySpanFile(
        id = "0af973ee-9a2a-4ac3-a330-d8baee101df7",
        accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
        bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
        libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
        targetURI = "https://www.example.com",
        timeStarted = "2024-10-15T00:00:00Z",
        timeEnded = "2024-10-15T00:00:30Z",
      )

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    assertEquals(1, this.selectSpans().size)
    assertTrue(Files.exists(badFile), "Unparseable file must be retained")
    assertTrue(!Files.exists(goodFile), "Parseable file must be deleted")
  }

  @Test
  fun testMigrationSkipsTornAuditTail() {
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingStopped")
    p.setProperty("Time", "2024-10-15T00:00:00Z")
    p.setProperty("LibraryID", "1014c482-0629-4a57-87ae-f9cc6e933397")
    p.setProperty("BookID", "cbd92367-f3e1-4310-a767-a07058271c2b")
    this.writeLegacyAuditRecord(p)
    this.writeLegacyAuditTail(
      "\n\u0000<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<properties>\n<entry key=\"Operation\">TimeTrackin",
    )

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    val events = this.selectEvents()
    assertEquals(1, events.size)
  }

  @Test
  fun testMigrationMovesAllDataTogether() {
    this.writeLegacySpanFile(
      id = "0af973ee-9a2a-4ac3-a330-d8baee101df7",
      accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
      bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
      libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
      targetURI = "https://www.example.com",
      timeStarted = "2024-10-15T00:00:00Z",
      timeEnded = "2024-10-15T00:00:30Z",
    )
    this.writeLegacyEntryFile(
      id = "01J2A3B4C5D6E7F8A9B0C1D2E3",
      accountID = "f858f8a0-2b5c-4c2f-9d3f-1a2b3c4d5e6f",
      bookID = "cbd92367-f3e1-4310-a767-a07058271c2b",
      libraryID = "urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b",
      targetURI = "https://www.example.com",
      minute = "2024-10-15T00:00:00Z",
      seconds = 30,
    )
    val p = Properties()
    p.setProperty("Operation", "TimeTrackingEntryCreated")
    p.setProperty("Time", "2024-10-15T00:02:00Z")
    p.setProperty("LibraryID", "1014c482-0629-4a57-87ae-f9cc6e933397")
    p.setProperty("BookID", "cbd92367-f3e1-4310-a767-a07058271c2b")
    p.setProperty("EntryID", "01J2A3B4C5D6E7F8A9B0C1D2E3")
    p.setProperty("DuringMinute", "2024-10-15T00:00:00Z")
    p.setProperty("Seconds", "30")
    this.writeLegacyAuditRecord(p)

    TimeTrackingMigration.create(
      database = this.database,
      legacyDirectory = this.legacyDirectory,
      diagnostics = this.diagnostics,
    ).migrate()

    assertEquals(1, this.selectSpans().size)
    assertEquals(1, this.selectEntries().size)
    assertEquals(1, this.selectEvents().size)
    assertTrue(
      !Files.exists(this.legacyDirectory.resolve("collector")),
      "Legacy collector directory must be deleted",
    )
    assertTrue(
      !Files.exists(this.legacyDirectory.resolve("sender")),
      "Legacy sender directory must be deleted",
    )
    assertTrue(
      !Files.exists(this.legacyDirectory.resolve("debug")),
      "Legacy debug directory must be deleted",
    )
  }
}
