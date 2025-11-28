package org.nypl.simplified.tests.opds.client

import android.content.Context
import com.io7m.jfunctional.Option
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
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFacet
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

class OPDSClientCompositeFacetTest {

  private val logger =
    LoggerFactory.getLogger(OPDSClientCompositeFacetTest::class.java)

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
            supportsBoundless = true,
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
      this.client.goTo(
        OPDSClientRequest.ExistingEntry(
          historyBehavior = OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY,
          FeedEntry.FeedEntryCorrupt(account0, book0, IllegalStateException())
        )
      ).get(5L, TimeUnit.SECONDS)
    }

    this.webServer.shutdown()
    this.exec.shutdown()
    this.exec.awaitTermination(5L, TimeUnit.SECONDS)
  }

  /**
   * The composite facet results in requests being made.
   */

  @Test
  fun testFeedCompositeFacetMissingButOK() {
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-none.xml"))
    )

    val f =
      this.client.goTo(
        OPDSClientRequest.ResolvedCompositeOPDS12Facet(
          historyBehavior = OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY,
          credentials = null,
          method = "GET",
          facet = FeedFacet.FeedFacetOPDS12Composite(
            facets = listOf(
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
                  group = "G",
                  title = "X",
                  groupType = Option.none()
                )
              )
            ),
            title = "Results",
            isActive = true
          )
        )
      )

    f.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    assertEquals(
      listOf("Initial", "Loading", "LoadedFeedWithoutGroups"),
      this.stateChanges
    )
  }

  /**
   * The composite facet requires a sequence of feeds that contain the right facets.
   */

  @Test
  fun testFeedCompositeFacetMissingRequired() {
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-none.xml"))
    )
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-none.xml"))
    )

    val f =
      this.client.goTo(
        OPDSClientRequest.ResolvedCompositeOPDS12Facet(
          historyBehavior = OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY,
          credentials = null,
          method = "GET",
          facet = FeedFacet.FeedFacetOPDS12Composite(
            facets = listOf(
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
                  group = "G",
                  title = "X",
                  groupType = Option.none()
                )
              ),
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
                  group = "H",
                  title = "Y",
                  groupType = Option.none()
                )
              )
            ),
            title = "Results",
            isActive = true
          )
        )
      )

    assertThrows<Exception> { f.get(5L, TimeUnit.SECONDS) }
    assertInstanceOf(OPDSState.Error::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    assertEquals(
      listOf("Initial", "Loading", "Error"),
      this.stateChanges
    )
  }

  /**
   * The right requests are made for composite facets.
   */

  @Test
  fun testFeedCompositeFacetOK() {
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-ok-0.xml"))
    )
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-ok-0.xml"))
    )

    val f =
      this.client.goTo(
        OPDSClientRequest.ResolvedCompositeOPDS12Facet(
          historyBehavior = OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY,
          credentials = null,
          method = "GET",
          facet = FeedFacet.FeedFacetOPDS12Composite(
            facets = listOf(
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/facet-G-X.xml"),
                  group = "G",
                  title = "X",
                  groupType = Option.none()
                )
              ),
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/facet-H-Y.xml"),
                  group = "H",
                  title = "Y",
                  groupType = Option.none()
                )
              )
            ),
            title = "Results",
            isActive = true
          )
        )
      )

    f.get(5L, TimeUnit.SECONDS)
    assertInstanceOf(OPDSState.LoadedFeedWithoutGroups::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    assertEquals(
      listOf("Initial", "Loading", "LoadedFeedWithoutGroups"),
      this.stateChanges
    )
  }

  /**
   * Grouped feeds cannot have facets.
   */

  @Test
  fun testFeedCompositeFacetGroups() {
    assertInstanceOf(OPDSState.Initial::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-groups.xml"))
    )
    this.webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(this.textOf("/org/nypl/simplified/tests/opds/client/facets-groups.xml"))
    )

    val f =
      this.client.goTo(
        OPDSClientRequest.ResolvedCompositeOPDS12Facet(
          historyBehavior = OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY,
          credentials = null,
          method = "GET",
          facet = FeedFacet.FeedFacetOPDS12Composite(
            facets = listOf(
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
                  group = "G",
                  title = "X",
                  groupType = Option.none()
                )
              ),
              FeedFacet.FeedFacetOPDS12Single(
                accountID = this.account0,
                opdsFacet = OPDSFacet(
                  isActive = true,
                  uri = URI.create("http://127.0.0.1:${this.webServer.port}/feed.xml"),
                  group = "H",
                  title = "Y",
                  groupType = Option.none()
                )
              )
            ),
            title = "Results",
            isActive = true
          )
        )
      )

    assertThrows<Exception> { f.get(5L, TimeUnit.SECONDS) }
    assertInstanceOf(OPDSState.Error::class.java, this.client.state.get())
    assertFalse(this.client.hasHistory)

    assertEquals(
      listOf("Initial", "Loading", "Error"),
      this.stateChanges
    )
  }

  private fun textOf(
    name: String
  ): String {
    val stream = OPDSClientCompositeFacetTest::class.java.getResourceAsStream(name)
      ?: throw IllegalStateException("No such resource: $name")
    return String(stream.readAllBytes(), UTF_8)
  }
}
