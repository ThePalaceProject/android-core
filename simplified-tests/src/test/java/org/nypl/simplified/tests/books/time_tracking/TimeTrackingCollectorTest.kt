package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import io.reactivex.subjects.PublishSubject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.books.time.tracking.TimeTrackingCollector
import org.nypl.simplified.books.time.tracking.TimeTrackingCollectorServiceType
import org.nypl.simplified.books.time.tracking.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.TimeTrackingStatus
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockProfile
import org.nypl.simplified.tests.mocking.MockProfilesController
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class TimeTrackingCollectorTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingCollectorTest::class.java)

  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val palaceIDWrong =
    PlayerPalaceID("e1cc010f-e88d-4c16-a12e-ab7b9f5c2065")

  private lateinit var profiles: MockProfilesController
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var collector: TimeTrackingCollectorServiceType
  private lateinit var inboxDirectory: Path
  private lateinit var debugDirectory: Path
  private lateinit var timeSegments: PublishSubject<PlayerTimeTracked>
  private lateinit var status: AttributeType<TimeTrackingStatus>
  private lateinit var accountRef: MockAccount
  private lateinit var profileRef: MockProfile
  private lateinit var accountProviderRef: AccountProviderType

  @BeforeEach
  fun setup(
    @TempDir debugDirectory: Path,
    @TempDir inboxDirectory: Path
  ) {
    this.resources = CloseableCollection.create()
    this.debugDirectory = debugDirectory
    this.inboxDirectory = inboxDirectory

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

    this.collector =
      TimeTrackingCollector.create(
        profiles = this.profiles,
        status = this.status,
        timeSegments = this.timeSegments,
        debugDirectory = debugDirectory,
        outputDirectory = inboxDirectory
      )

    this.resources.add(AutoCloseable { this.collector.close() })
  }

  @AfterEach
  fun tearDown()
  {
    this.resources.close()
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorRecordsSpans() {
    this.status.set(TimeTrackingStatus.Active(
      accountID = this.accountRef.id,
      bookId = this.palaceID,
      libraryId = "1014c482-0629-4a57-87ae-f9cc6e933397",
      timeTrackingUri = URI.create("http://www.example.com")
    ))

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceID,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        rate = 1.0
      )
    )

    this.collector.awaitWrite(1L, TimeUnit.SECONDS)

    val fileExpected =
      this.inboxDirectory.resolve("0af973ee-9a2a-4ac3-a330-d8baee101df7.ttspan")

    val filesNow = Files.list(this.inboxDirectory).collect(Collectors.toUnmodifiableList())
    this.logger.debug("Files now: {}", filesNow)
    if (!filesNow.contains(fileExpected)) {
      throw IllegalStateException("Files not written!")
    }

    val o = TimeTrackingReceivedSpan.ofFile(fileExpected)
    assertEquals(
      OffsetDateTime.parse("2024-10-10T00:00:00Z"),
      o.timeStarted
    )
    assertEquals(
      OffsetDateTime.parse("2024-10-10T00:00:30Z"),
      o.timeEnded
    )
    assertEquals(
      UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
      o.id
    )
    assertEquals(
      this.palaceID,
      o.bookID
    )
    assertEquals(
      this.accountRef.id,
      o.accountID
    )
    assertEquals(
      URI.create("http://www.example.com"),
      o.targetURI
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
        rate = 1.0
      )
    )

    Thread.sleep(1_000L)

    val filesNow = Files.list(this.inboxDirectory).collect(Collectors.toUnmodifiableList())
    this.logger.debug("Files now: {}", filesNow)
    if (filesNow.isNotEmpty()) {
      throw IllegalStateException("File created!")
    }
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorIgnoresInactiveSpansAfterActivity() {
    this.status.set(TimeTrackingStatus.Active(
      accountID = this.accountRef.id,
      bookId = this.palaceID,
      libraryId = "1014c482-0629-4a57-87ae-f9cc6e933397",
      timeTrackingUri = URI.create("http://www.example.com")
    ))
    this.status.set(TimeTrackingStatus.Inactive)

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceID,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        rate = 1.0
      )
    )

    Thread.sleep(1_000L)

    val filesNow = Files.list(this.inboxDirectory).collect(Collectors.toUnmodifiableList())
    this.logger.debug("Files now: {}", filesNow)
    if (filesNow.isNotEmpty()) {
      throw IllegalStateException("File created!")
    }
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testCollectorIgnoresSpansForWrongBook() {
    this.status.set(TimeTrackingStatus.Active(
      accountID = this.accountRef.id,
      bookId = this.palaceID,
      libraryId = "1014c482-0629-4a57-87ae-f9cc6e933397",
      timeTrackingUri = URI.create("http://www.example.com")
    ))

    this.timeSegments.onNext(
      PlayerTimeTracked.create(
        id = UUID.fromString("0af973ee-9a2a-4ac3-a330-d8baee101df7"),
        bookTrackingId = this.palaceIDWrong,
        timeStarted = OffsetDateTime.parse("2024-10-10T00:00:00Z"),
        timeEnded = OffsetDateTime.parse("2024-10-10T00:00:30Z"),
        rate = 1.0
      )
    )

    Thread.sleep(1_000L)

    val filesNow = Files.list(this.inboxDirectory).collect(Collectors.toUnmodifiableList())
    this.logger.debug("Files now: {}", filesNow)
    if (filesNow.isNotEmpty()) {
      throw IllegalStateException("File created!")
    }
  }
}
