package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import io.reactivex.subjects.PublishSubject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.books.time.tracking.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.TimeTrackingRequest
import org.nypl.simplified.books.time.tracking.TimeTrackingSender
import org.nypl.simplified.books.time.tracking.TimeTrackingSenderServiceType
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponse
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseSummary
import org.nypl.simplified.tests.mocking.FakeTimeTrackingHTTPCalls
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockProfile
import org.nypl.simplified.tests.mocking.MockProfilesController
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class TimeTrackingSenderTest {

  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val palaceIDWrong =
    PlayerPalaceID("e1cc010f-e88d-4c16-a12e-ab7b9f5c2065")

  private lateinit var accountProviderRef: AccountProviderType
  private lateinit var accountID: AccountID
  private lateinit var account: MockAccount
  private lateinit var profile: MockProfile
  private lateinit var profiles: MockProfilesController
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var sender: TimeTrackingSenderServiceType
  private lateinit var inboxDirectory: Path
  private lateinit var debugDirectory: Path
  private lateinit var timeSegments: PublishSubject<PlayerTimeTracked>
  private lateinit var httpCalls: FakeTimeTrackingHTTPCalls

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
    this.profile =
      this.profiles.profileList[0]
    this.account =
      this.profile.accountList[0]
    this.accountID =
      this.account.id
    this.accountProviderRef =
      this.account.provider

    this.httpCalls =
      FakeTimeTrackingHTTPCalls()
    this.timeSegments =
      PublishSubject.create()

    this.resources.add(AutoCloseable { this.timeSegments.onComplete() })

    this.sender =
      TimeTrackingSender.create(
        profiles = this.profiles,
        httpCalls = this.httpCalls,
        frequency = Duration.ofMillis(100L),
        debugDirectory = debugDirectory,
        inputDirectory = inboxDirectory
      )

    this.resources.add(AutoCloseable { this.sender.close() })
  }

  @AfterEach
  fun tearDown() {
    this.resources.close()
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderWritesNothing() {
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(0, this.httpCalls.requests.size)
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderUnparseableFile() {
    Files.write(this.inboxDirectory.resolve("x.tteo"), "Not valid!".toByteArray())

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(0, this.httpCalls.requests.size)

    assertEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
  }

  /**
   * An entry is sent and then removed on success.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderEntryWritten0() {
    val entry =
      TimeTrackingEntryOutgoing(
        accountID = this.accountID,
        libraryID = this.accountProviderRef.id,
        bookID = this.palaceID,
        targetURI = URI.create("https://www.example.com"),
        timeEntry = TimeTrackingEntry(
          id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
          duringMinute = "2024-10-17T00:00:00",
          secondsPlayed = 60
        )
      )

    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(
          TimeTrackingServerResponseEntry(
            id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
            message = "OK",
            status = 200
          )
        ),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      )
    )

    val file =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo")
    val fileTmp =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo.tmp")

    Files.newOutputStream(fileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
      entry.toProperties().store(s, "")
      s.flush()
    }
    Files.move(
      fileTmp,
      file,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[0]
    )
    assertEquals(1, this.httpCalls.requests.size)

    assertNotEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
    assertFalse(Files.exists(file), "File must have been deleted.")
  }

  /**
   * An entry is sent and then removed on success.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderEntryWritten1() {
    val entry =
      TimeTrackingEntryOutgoing(
        accountID = this.accountID,
        libraryID = this.accountProviderRef.id,
        bookID = this.palaceID,
        targetURI = URI.create("https://www.example.com"),
        timeEntry = TimeTrackingEntry(
          id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
          duringMinute = "2024-10-17T00:00:00",
          secondsPlayed = 60
        )
      )

    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      )
    )

    val file =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo")
    val fileTmp =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo.tmp")

    Files.newOutputStream(fileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
      entry.toProperties().store(s, "")
      s.flush()
    }
    Files.move(
      fileTmp,
      file,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[0]
    )
    assertEquals(1, this.httpCalls.requests.size)

    assertNotEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
    assertFalse(Files.exists(file), "File must have been deleted.")
  }

  /**
   * An entry is retried on failure.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderEntryRetries0() {
    val entry =
      TimeTrackingEntryOutgoing(
        accountID = this.accountID,
        libraryID = this.accountProviderRef.id,
        bookID = this.palaceID,
        targetURI = URI.create("https://www.example.com"),
        timeEntry = TimeTrackingEntry(
          id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
          duringMinute = "2024-10-17T00:00:00",
          secondsPlayed = 60
        )
      )

    /*
     * The server returns an error twice, and then returns success on the third attempt.
     */

    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(
          TimeTrackingServerResponseEntry(
            id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
            message = "Error!",
            status = 400
          )
        ),
        summary = TimeTrackingServerResponseSummary(
          failures = 1,
          successes = 0,
          total = 1
        )
      ),
    )
    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(
          TimeTrackingServerResponseEntry(
            id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
            message = "Error!",
            status = 400
          )
        ),
        summary = TimeTrackingServerResponseSummary(
          failures = 1,
          successes = 0,
          total = 1
        )
      )
    )
    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      )
    )

    val file =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo")
    val fileTmp =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo.tmp")

    Files.newOutputStream(fileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
      entry.toProperties().store(s, "")
      s.flush()
    }
    Files.move(
      fileTmp,
      file,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[0]
    )
    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[1]
    )
    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[2]
    )
    assertEquals(3, this.httpCalls.requests.size)

    assertNotEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
    assertFalse(Files.exists(file), "File must have been deleted.")
  }

  /**
   * An entry is retried on failure.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderEntryRetries1() {
    val entry =
      TimeTrackingEntryOutgoing(
        accountID = this.accountID,
        libraryID = this.accountProviderRef.id,
        bookID = this.palaceID,
        targetURI = URI.create("https://www.example.com"),
        timeEntry = TimeTrackingEntry(
          id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
          duringMinute = "2024-10-17T00:00:00",
          secondsPlayed = 60
        )
      )

    /*
     * The server returns an error twice, and then returns success on the third attempt.
     */

    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 1,
          successes = 0,
          total = 1
        )
      )
    )
    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 1,
          successes = 0,
          total = 1
        )
      )
    )
    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      )
    )

    val file =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo")
    val fileTmp =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo.tmp")

    Files.newOutputStream(fileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
      entry.toProperties().store(s, "")
      s.flush()
    }
    Files.move(
      fileTmp,
      file,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[0]
    )
    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[1]
    )
    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[2]
    )
    assertEquals(3, this.httpCalls.requests.size)

    assertNotEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
    assertFalse(Files.exists(file), "File must have been deleted.")
  }

  /**
   * An entry is retried on failure.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderEntryRetries2() {
    val entry =
      TimeTrackingEntryOutgoing(
        accountID = this.accountID,
        libraryID = this.accountProviderRef.id,
        bookID = this.palaceID,
        targetURI = URI.create("https://www.example.com"),
        timeEntry = TimeTrackingEntry(
          id = "01JAD2H8Y8DY3K0WZVBXZH3MBM",
          duringMinute = "2024-10-17T00:00:00",
          secondsPlayed = 60
        )
      )

    /*
     * The HTTP call raises twice, and then returns success on the third attempt.
     */

    this.httpCalls.crashes.add(IOException("Ouch!"))
    this.httpCalls.crashes.add(IOException("Ouch!"))
    this.httpCalls.responses.add(
      TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      )
    )

    val file =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo")
    val fileTmp =
      this.inboxDirectory.resolve("01JAD2H8Y8DY3K0WZVBXZH3MBM.tteo.tmp")

    Files.newOutputStream(fileTmp, WRITE, CREATE, TRUNCATE_EXISTING).use { s ->
      entry.toProperties().store(s, "")
      s.flush()
    }
    Files.move(
      fileTmp,
      file,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)
    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[0]
    )
    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[1]
    )
    assertEquals(
      TimeTrackingRequest(
        bookId = this.palaceID.value,
        libraryId = this.accountProviderRef.id,
        timeTrackingUri = URI.create("https://www.example.com"),
        timeEntries = listOf(entry.timeEntry)
      ),
      this.httpCalls.requests[2]
    )
    assertEquals(3, this.httpCalls.requests.size)

    assertNotEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
    assertFalse(Files.exists(file), "File must have been deleted.")
  }
}
