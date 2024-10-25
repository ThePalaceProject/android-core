package org.nypl.simplified.tests.books.time_tracking

import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import io.reactivex.subjects.PublishSubject
import okio.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.mockito.Mockito
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.time.tracking.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.TimeTrackingHTTPCallsType
import org.nypl.simplified.books.time.tracking.TimeTrackingRequest
import org.nypl.simplified.books.time.tracking.TimeTrackingSender
import org.nypl.simplified.books.time.tracking.TimeTrackingSenderServiceType
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponse
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingServerResponseSummary
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class TimeTrackingSenderTest {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingSenderTest::class.java)

  private val accountID =
    AccountID(UUID.randomUUID())
  private val palaceID =
    PlayerPalaceID("cbd92367-f3e1-4310-a767-a07058271c2b")
  private val palaceIDWrong =
    PlayerPalaceID("e1cc010f-e88d-4c16-a12e-ab7b9f5c2065")

  private lateinit var profiles: ProfilesControllerType
  private lateinit var resources: CloseableCollectionType<ClosingResourceFailedException>
  private lateinit var sender: TimeTrackingSenderServiceType
  private lateinit var inboxDirectory: Path
  private lateinit var debugDirectory: Path
  private lateinit var timeSegments: PublishSubject<PlayerTimeTracked>
  private lateinit var accountRef: AccountType
  private lateinit var profileRef: ProfileType
  private lateinit var accountProviderRef: AccountProviderType
  private lateinit var httpCalls: TimeTrackingHTTPCallsType

  @BeforeEach
  fun setup(
    @TempDir debugDirectory: Path,
    @TempDir inboxDirectory: Path
  ) {
    this.resources = CloseableCollection.create()
    this.debugDirectory = debugDirectory
    this.inboxDirectory = inboxDirectory

    this.profiles =
      Mockito.mock(ProfilesControllerType::class.java)
    this.accountRef =
      Mockito.mock(AccountType::class.java)
    this.profileRef =
      Mockito.mock(ProfileType::class.java)
    this.accountProviderRef =
      Mockito.mock(AccountProviderType::class.java)

    Mockito.`when`(this.profiles.profileCurrent())
      .thenReturn(this.profileRef)
    Mockito.`when`(this.profileRef.account(any()))
      .thenReturn(this.accountRef)
    Mockito.`when`(this.accountRef.provider)
      .thenReturn(this.accountProviderRef)
    Mockito.`when`(this.accountProviderRef.id)
      .thenReturn(URI.create("urn:uuid:d36a27ab-acd4-4e49-b2cc-19086c780cfb"))

    this.httpCalls =
      Mockito.mock(TimeTrackingHTTPCallsType::class.java)

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

    Mockito.verifyNoInteractions(this.httpCalls)
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testSenderUnparseableFile() {
    Files.write(this.inboxDirectory.resolve("x.tteo"), "Not valid!".toByteArray())

    this.sender.awaitWrite(1L, TimeUnit.SECONDS)

    Mockito.verifyNoInteractions(this.httpCalls)

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

    Mockito.`when`(this.httpCalls.registerTimeTrackingInfo(any(), any()))
      .thenReturn(TimeTrackingServerResponse(
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
      ))

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

    Mockito.verify(this.httpCalls, Times(1))
      .registerTimeTrackingInfo(
        request = TimeTrackingRequest(
          bookId = this.palaceID.value,
          libraryId = this.accountProviderRef.id,
          timeTrackingUri = URI.create("https://www.example.com"),
          timeEntries = listOf(entry.timeEntry)
        ),
        account = this.accountRef
      )

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

    Mockito.`when`(this.httpCalls.registerTimeTrackingInfo(any(), any()))
      .thenReturn(TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      ))

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

    Mockito.verify(this.httpCalls, Times(1))
      .registerTimeTrackingInfo(
        request = TimeTrackingRequest(
          bookId = this.palaceID.value,
          libraryId = this.accountProviderRef.id,
          timeTrackingUri = URI.create("https://www.example.com"),
          timeEntries = listOf(entry.timeEntry)
        ),
        account = this.accountRef
      )

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

    Mockito.`when`(this.httpCalls.registerTimeTrackingInfo(any(), any()))
      .thenReturn(TimeTrackingServerResponse(
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
      ))
      .thenReturn(TimeTrackingServerResponse(
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
      ))
      .thenReturn(TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      ))

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

    Mockito.verify(this.httpCalls, Times(3))
      .registerTimeTrackingInfo(
        request = TimeTrackingRequest(
          bookId = this.palaceID.value,
          libraryId = this.accountProviderRef.id,
          timeTrackingUri = URI.create("https://www.example.com"),
          timeEntries = listOf(entry.timeEntry)
        ),
        account = this.accountRef
      )

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

    Mockito.`when`(this.httpCalls.registerTimeTrackingInfo(any(), any()))
      .thenReturn(TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 1,
          successes = 0,
          total = 1
        )
      ))
      .thenReturn(TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 1,
          successes = 0,
          total = 1
        )
      ))
      .thenReturn(TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      ))

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

    Mockito.verify(this.httpCalls, Times(3))
      .registerTimeTrackingInfo(
        request = TimeTrackingRequest(
          bookId = this.palaceID.value,
          libraryId = this.accountProviderRef.id,
          timeTrackingUri = URI.create("https://www.example.com"),
          timeEntries = listOf(entry.timeEntry)
        ),
        account = this.accountRef
      )

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

    Mockito.`when`(this.httpCalls.registerTimeTrackingInfo(any(), any()))
      .thenThrow(java.io.IOException("Ouch!"))
      .thenThrow(java.io.IOException("Ouch!"))
      .thenReturn(TimeTrackingServerResponse(
        responses = listOf(),
        summary = TimeTrackingServerResponseSummary(
          failures = 0,
          successes = 1,
          total = 1
        )
      ))

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

    Mockito.verify(this.httpCalls, Times(3))
      .registerTimeTrackingInfo(
        request = TimeTrackingRequest(
          bookId = this.palaceID.value,
          libraryId = this.accountProviderRef.id,
          timeTrackingUri = URI.create("https://www.example.com"),
          timeEntries = listOf(entry.timeEntry)
        ),
        account = this.accountRef
      )

    assertNotEquals(
      listOf<Path>(),
      Files.list(this.debugDirectory).collect(Collectors.toUnmodifiableList())
    )
    assertFalse(Files.exists(file), "File must have been deleted.")
  }
}
