package org.nypl.simplified.tests.opds.client

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.vanilla.LSHTTPProblemReportParsers
import org.librarysimplified.http.vanilla.internal.LSHTTPClient
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.opds.core.OPDSSearchParserType
import org.nypl.simplified.tests.mocking.MockBundledContentResolver
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClient
import org.thepalaceproject.opds.client.OPDSClientParameters
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSClientType
import org.thepalaceproject.opds.client.OPDSState
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OPDSClientTest {

  private val logger =
    LoggerFactory.getLogger(OPDSClientTest::class.java)

  private val account0 =
    AccountID.generate()
  private val book0 =
    BookID.create("abcd")

  private lateinit var client: OPDSClientType
  private lateinit var contentResolver: MockContentResolver
  private lateinit var context: Context
  private lateinit var exec: ExecutorService
  private lateinit var feedLoader: FeedLoaderType
  private lateinit var feedTransport: FeedHTTPTransport
  private lateinit var parser: OPDSFeedParserType
  private lateinit var searchParser: OPDSSearchParserType
  private lateinit var webServer: MockWebServer
  private lateinit var stateChanges: MutableList<String>

  @BeforeEach
  fun setup() {
    this.webServer =
      MockWebServer()

    this.exec =
      Executors.newCachedThreadPool()
    this.context =
      Mockito.mock(Context::class.java)
    this.contentResolver =
      MockContentResolver()
    this.parser =
      OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
    this.searchParser =
      OPDSSearchParser.newParser()
    this.feedTransport =
      FeedHTTPTransport(
        LSHTTPClient(
          context = this.context,
          configuration = LSHTTPClientConfiguration(
            applicationName = "palace",
            applicationVersion = "1.0.0"
          ),
          problemReportParsers = LSHTTPProblemReportParsers(),
          interceptors = listOf()
        )
      )

    this.feedLoader =
      FeedLoader.create(
        bookFormatSupport = BookFormatSupport.create(
          BookFormatSupportParameters(
            supportsPDF = true,
            supportsAdobeDRM = true,
            supportsAxisNow = true,
            supportsAudioBooks = null,
            supportsLCP = true
          )
        ),
        contentResolver = MockContentResolver(),
        exec = this.exec,
        parser = this.parser,
        searchParser = this.searchParser,
        transport = this.feedTransport,
        bundledContent = MockBundledContentResolver()
      )

    this.client =
      OPDSClient.create(
        OPDSClientParameters(
          runOnUI = { r -> r.run() },
          checkOnUI = { },
          feedLoader = this.feedLoader,
          name = "Test"
        )
      )

    this.stateChanges = mutableListOf()
    this.client.state.subscribe { _, newValue ->
      this.stateChanges.add(newValue.javaClass.simpleName)
    }
  }

  @AfterEach
  fun tearDown() {
    this.logger.info("Shutting down webserver...")
    this.client.close()

    assertThrows<ExecutionException> {
      this.client.goBack().get(5L, TimeUnit.SECONDS)
    }
    assertThrows<ExecutionException> {
      this.client.loadMore().get(5L, TimeUnit.SECONDS)
    }
    assertThrows<ExecutionException> {
      this.client.goTo(
        OPDSClientRequest.ExistingEntry(
          FeedEntry.FeedEntryCorrupt(account0, book0, IllegalStateException())
        )
      ).get(5L, TimeUnit.SECONDS)
    }

    this.webServer.shutdown()
    this.exec.shutdown()
    this.exec.awaitTermination(5L, TimeUnit.SECONDS)
  }

  @Test
  fun testFeedFails() {
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("urn:nonexistent"),
          credentials = null,
          method = "GET"
        )
      )

    f.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.Error::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    assertEquals(
      listOf("Initial", "Loading", "Error"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedUnparseable() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("Does this look like some kind of feed to you?")
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.Error::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "Error"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedUnauthenticated() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(401)
        .setBody("No entry.")
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.Error::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "Error"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedWithoutGroups() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/errorsBorrowing.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(2, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "LoadedFeedWithoutGroups"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedWithoutGroupsCancel() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/errorsBorrowing.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    this.logger.debug("Cancelling task...")
    f.cancel(true)

    this.logger.debug("Checking if task is cancelled...")
    try {
      f.get(5L, TimeUnit.SECONDS)
    } catch (e: Throwable) {
      this.logger.debug("Get raised exception: ", e)
    } finally {
      Thread.sleep(1_000L)
    }

    this.logger.debug("Checking state...")
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "Initial"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedWithGroups() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/acquisition-fiction-0.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f0 =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f0.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedWithGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(0, this.client.entriesUngrouped.get().size)
    assertEquals(9, this.client.entriesGrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "LoadedFeedWithGroups"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedWithGroupsCancel() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/acquisition-fiction-0.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    this.logger.debug("Cancelling task...")
    f.cancel(true)

    this.logger.debug("Checking if task is cancelled...")
    try {
      f.get(5L, TimeUnit.SECONDS)
    } catch (e: Throwable) {
      this.logger.debug("Get raised exception: ", e)
    } finally {
      Thread.sleep(1_000L)
    }

    this.logger.debug("Checking state...")
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "Initial"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedPagination() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/next-0.xml"))
    )
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/next-1.xml"))
    )
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/next-2.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f0 =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f0.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(3, this.client.entriesUngrouped.get().size)
    assertEquals(0, this.client.entriesGrouped.get().size)

    val f1 = this.client.loadMore()
    f1.get(5L, TimeUnit.SECONDS)

    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(2, this.webServer.requestCount)
    assertEquals(6, this.client.entriesUngrouped.get().size)
    assertEquals(0, this.client.entriesGrouped.get().size)

    val f2 = this.client.loadMore()
    f2.get(5L, TimeUnit.SECONDS)

    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(3, this.webServer.requestCount)
    assertEquals(9, this.client.entriesUngrouped.get().size)
    assertEquals(0, this.client.entriesGrouped.get().size)

    val f3 = this.client.loadMore()
    f3.get(5L, TimeUnit.SECONDS)

    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(3, this.webServer.requestCount)
    assertEquals(9, this.client.entriesUngrouped.get().size)
    assertEquals(0, this.client.entriesGrouped.get().size)

    assertEquals(
      listOf(
        "Initial",
        "Loading",
        "LoadedFeedWithoutGroups",
        "LoadedFeedWithoutGroups",
        "LoadedFeedWithoutGroups"
      ),
      this.stateChanges
    )
  }

  @Test
  fun testFeedEntry() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/errorsBorrowing.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f0 =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f0.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(2, this.client.entriesUngrouped.get().size)

    val f1 =
      this.client.goTo(
        OPDSClientRequest.ExistingEntry(this.client.entriesUngrouped.get()[0])
      )

    f1.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedEntry::class.java, this.client.state.get())
    assertTrue(this.client.hasHistory)
    assertEquals(1, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf("Initial", "Loading", "LoadedFeedWithoutGroups", "LoadedFeedEntry"),
      this.stateChanges
    )
  }

  @Test
  fun testFeedHistory() {
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/acquisition-fiction-0.xml"))
    )
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/errorsBorrowing.xml"))
    )

    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    val f0 =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f0.get(5L, TimeUnit.SECONDS)

    val f1 =
      this.client.goTo(
        OPDSClientRequest.NewFeed(
          accountID = this.account0,
          uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
          credentials = null,
          method = "GET"
        )
      )

    f1.get(5L, TimeUnit.SECONDS)

    val f2 =
      this.client.goTo(
        OPDSClientRequest.ExistingEntry(this.client.entriesUngrouped.get()[0])
      )

    f2.get(5L, TimeUnit.SECONDS)

    assertTrue(this.client.hasHistory)
    assertEquals(2, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    this.client.goBack().get(5L, TimeUnit.SECONDS)

    assertTrue(this.client.hasHistory)
    assertEquals(2, this.webServer.requestCount)
    assertEquals(0, this.client.entriesGrouped.get().size)
    assertEquals(2, this.client.entriesUngrouped.get().size)

    this.client.goBack().get(5L, TimeUnit.SECONDS)

    assertFalse(this.client.hasHistory)
    assertEquals(2, this.webServer.requestCount)
    assertEquals(9, this.client.entriesGrouped.get().size)
    assertEquals(0, this.client.entriesUngrouped.get().size)

    assertEquals(
      listOf(
        "Initial",
        "Loading",
        "LoadedFeedWithGroups",
        "Loading",
        "LoadedFeedWithoutGroups",
        "LoadedFeedEntry",
        "LoadedFeedWithoutGroups",
        "LoadedFeedWithGroups"
      ),
      this.stateChanges
    )
  }

  private fun textOf(
    name: String
  ): String {
    val stream = OPDSClientTest::class.java.getResourceAsStream(name)
      ?: throw IllegalStateException("No such resource: $name")
    return String(stream.readAllBytes(), UTF_8)
  }
}
