package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.time.tracking.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.TimeTrackingMerge
import org.nypl.simplified.books.time.tracking.TimeTrackingMergeServiceType
import org.nypl.simplified.books.time.tracking.TimeTrackingReceivedSpan
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class TimeTrackingMergeTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingMergeTest::class.java)

  private val accountID =
    AccountID(UUID.randomUUID())
  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val palaceIDWrong =
    PlayerPalaceID("e1cc010f-e88d-4c16-a12e-ab7b9f5c2065")
  private val targetURI =
    URI.create("https://www.example.com")

  @Volatile
  private lateinit var timeNow: OffsetDateTime
  private lateinit var clock: () -> OffsetDateTime
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var inboxDirectory: Path
  private lateinit var outboxDirectory: Path
  private lateinit var merge: TimeTrackingMergeServiceType

  @BeforeEach
  fun setup(
    @TempDir outboxDirectory: Path,
    @TempDir inboxDirectory: Path
  ) {
    this.timeNow = OffsetDateTime.now()
    this.clock = { this.timeNow }

    this.resources = CloseableCollection.create()
    this.outboxDirectory = outboxDirectory
    this.inboxDirectory = inboxDirectory

    this.merge =
      TimeTrackingMerge.create(
        outputDirectory = outboxDirectory,
        inputDirectory = inboxDirectory,
        clock = this.clock,
        frequency = Duration.ofMillis(100L)
      )

    this.resources.add(AutoCloseable { this.merge.close() })
  }

  @AfterEach
  fun tearDown() {
    this.resources.close()
  }

  @Test
  fun testMergeOneMinuteBoundary() {
    val spans = listOf(
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:01:30Z"),
        targetURI = this.targetURI
      )
    )

    /*
     * An entry that spans a minute boundary produces two time tracking entries.
     */

    val totalExpected = 60

    val entries =
      TimeTrackingMerge.mergeEntries(spans)
    val totalReceived =
      entries.sumOf { e -> e.timeEntry.secondsPlayed }

    assertEquals(totalExpected, totalReceived)
    assertEquals(2, entries.size)
  }

  @Test
  fun testMergeOverlapping() {
    val spans = listOf(
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:45Z"),
        targetURI = this.targetURI
      ),
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:45Z"),
        targetURI = this.targetURI
      )
    )

    /*
     * A silly answer to a silly question: We're claiming that we've listened to a book
     * twice in the exact same time period. The results simply sum and are clamped to sixty
     * seconds for the one minute they fall within.
     */

    val totalExpected = 60

    val entries =
      TimeTrackingMerge.mergeEntries(spans)
    val totalReceived =
      entries.sumOf { e -> e.timeEntry.secondsPlayed }

    assertEquals(totalExpected, totalReceived)
    assertEquals(1, entries.size)
  }

  @Test
  fun testMerge0() {
    val spans = listOf(
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:20Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        targetURI = this.targetURI
      ),
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:31Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:50Z"),
        targetURI = this.targetURI
      ),
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:51Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:01:40Z"),
        targetURI = this.targetURI
      )
    )

    /*
     * The entries sum to the original length of the spans.
     */

    val totalExpected = spans.sumOf { span ->
      Duration.between(span.timeStarted, span.timeEnded).toMillis() / 1000
    }.toInt()

    val entries =
      TimeTrackingMerge.mergeEntries(spans)
    val totalReceived =
      entries.sumOf { e -> e.timeEntry.secondsPlayed }

    assertEquals(totalExpected, totalReceived)
    assertEquals(2, entries.size)
  }

  @Test
  fun testMergeEntries() {
    val spans = listOf(
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:20Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:30Z"),
        targetURI = this.targetURI
      ),
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:31Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:00:50Z"),
        targetURI = this.targetURI
      ),
      TimeTrackingReceivedSpan(
        id = UUID.randomUUID(),
        accountID = this.accountID,
        bookID = this.palaceID,
        libraryID = URI.create("urn:uuid:f19fc9b0-6259-4e0a-a76b-2246543e8b6b"),
        timeStarted = OffsetDateTime.parse("2024-10-15T00:00:51Z"),
        timeEnded = OffsetDateTime.parse("2024-10-15T00:01:40Z"),
        targetURI = this.targetURI
      )
    )

    val totalExpected = spans.sumOf { span ->
      Duration.between(span.timeStarted, span.timeEnded).toMillis() / 1000
    }.toInt()

    spans.forEach { s ->
      val fileTmp =
        this.inboxDirectory.resolve("${s.id}.ttspan.tmp")
      val file =
        this.inboxDirectory.resolve("${s.id}.ttspan")

      Files.write(fileTmp, s.toBytes())
      Files.move(fileTmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
      Files.setLastModifiedTime(
        file,
        FileTime.from(this.timeNow.toInstant())
      )
    }

    /*
     * Wind the clock forward two minutes so that the previously written spans are now eligible
     * for processing.
     */

    this.timeNow = this.timeNow.plusMinutes(2L)
    this.merge.awaitTick(1L, TimeUnit.SECONDS)
    this.merge.awaitTick(1L, TimeUnit.SECONDS)

    val inboxNow = Files.list(this.inboxDirectory).collect(Collectors.toUnmodifiableList())
    this.logger.debug("Inbox now: {}", inboxNow)
    if (inboxNow.isNotEmpty()) {
      throw IllegalStateException("Files not processed!")
    }

    val outboxNow = Files.list(this.outboxDirectory).collect(Collectors.toUnmodifiableList())
    this.logger.debug("Outbox now: {}", inboxNow)

    val e0 =
      TimeTrackingEntryOutgoing.ofFile(outboxNow.get(0))
    val e1 =
      TimeTrackingEntryOutgoing.ofFile(outboxNow.get(1))

    assertEquals(
      totalExpected,
      e0.timeEntry.secondsPlayed + e1.timeEntry.secondsPlayed
    )
    assertNotEquals(
      e0.timeEntry.id,
      e1.timeEntry.id
    )
    assertNotEquals(
      e0.timeEntry.duringMinute,
      e1.timeEntry.duringMinute
    )
    assertEquals(2, outboxNow.size)
  }
}
