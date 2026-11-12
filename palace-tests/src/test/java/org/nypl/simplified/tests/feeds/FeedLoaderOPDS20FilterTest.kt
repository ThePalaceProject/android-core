package org.nypl.simplified.tests.feeds

import one.irradia.mime.api.MIMEType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedFetchResult
import org.nypl.simplified.feeds.api.FeedFetchType
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.FeedLoader
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Tests for the [FeedLoader] with OPDS 2.0 feeds that use
 * `application/opds-publication+json` as the acquisition link type
 * with indirect acquisitions carrying the actual content type.
 *
 * These tests verify that the `showOnlySupportedBooks` filter does
 * NOT strip entries from OPDS 2.0 feeds where the source type is
 * the OPDS 2.0 wrapper type.
 */
class FeedLoaderOPDS20FilterTest {

  private lateinit var exec: java.util.concurrent.ExecutorService

  @BeforeEach
  fun setup() {
    this.exec = Executors.newSingleThreadExecutor()
  }

  @AfterEach
  fun tearDown() {
    this.exec.shutdown()
  }

  private val accountID: AccountID = AccountID(UUID.randomUUID())
  private val testURI: URI = URI("https://example.com/feed/")

  private val opds20ContentType: MIMEType =
    MIMEType("application", "opds+json", mapOf())

  private fun createLoader(
    body: String,
    contentType: MIMEType?,
  ): FeedLoader {
    return FeedLoader(
      bookFormatSupport =
        BookFormatSupport.create(
          BookFormatSupportParameters(
            supportsPDF = false,
            supportsLCP = true,
            supportsAdobeDRM = false,
            supportsBoundless = false,
            supportsAudioBooks = null,
          ),
        ),
      exec = this.exec,
      fetch =
        FeedFetchType { _, _, _ ->
          FeedFetchResult(
            body = ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8)),
            accessToken = null,
            contentType = contentType,
          )
        },
    )
  }

  /**
   * An OPDS 2.0 feed with a single publication whose acquisition link
   * uses `application/opds-publication+json` as the type, with an
   * indirect acquisition of `application/epub+zip`.
   *
   * The entry must survive the `showOnlySupportedBooks` filter.
   */

  @Test
  fun testOPDS20IndirectAcquisitionSurvivesFilter() {
    val json = this.resource("opds20-indirect-acquisition-survives-filter.json")

    val loader = this.createLoader(json, this.opds20ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertEquals(1, (feed as org.nypl.simplified.feeds.api.FeedData).size)
    val entry = feed.entries[0] as FeedEntryBook
    Assertions.assertEquals("Test Book", entry.title)
  }

  /**
   * An OPDS 2.0 feed with groups containing publications. Each
   * publication uses the OPDS 2.0 indirect acquisition structure.
   *
   * The groups must have non-empty `groupEntries` after filtering.
   */

  @Test
  fun testOPDS20GroupsSurviveFilter() {
    val json = this.resource("opds20-groups-survive-filter.json")

    val loader = this.createLoader(json, this.opds20ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertTrue(feed is FeedDataWithGroups)
    val groupedFeed = feed as FeedDataWithGroups

    Assertions.assertEquals(2, groupedFeed.groups.size)

    val groupA = groupedFeed.groups[0]
    Assertions.assertEquals("Group A", groupA.groupTitle)
    Assertions.assertEquals(2, groupA.groupEntries.size)
    Assertions.assertEquals("Book A1", (groupA.groupEntries[0] as FeedEntryBook).title)
    Assertions.assertEquals("Book A2", (groupA.groupEntries[1] as FeedEntryBook).title)

    val groupB = groupedFeed.groups[1]
    Assertions.assertEquals("Group B", groupB.groupTitle)
    Assertions.assertEquals(1, groupB.groupEntries.size)
    Assertions.assertEquals("Book B1", (groupB.groupEntries[0] as FeedEntryBook).title)
  }

  /**
   * An OPDS 2.0 feed with an LCP-protected book. The indirect
   * acquisition chain is:
   *   source: application/opds-publication+json
   *   indirect[0]: application/vnd.readium.lcp.license.v1.0+json
   *   indirect[0].children[0]: application/epub+zip
   *
   * The entry must survive the filter when LCP is enabled.
   */

  @Test
  fun testOPDS20LCPIndirectAcquisitionSurvivesFilter() {
    val json = this.resource("opds20-lcp-indirect-acquisition-survives-filter.json")

    val loader = this.createLoader(json, this.opds20ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertEquals(1, (feed as org.nypl.simplified.feeds.api.FeedData).size)
    val entry = feed.entries[0] as FeedEntryBook
    Assertions.assertEquals("LCP Book", entry.title)
  }

  /**
   * Regression test: the server's loans feed uses the bare
   * `http://opds-spec.org/acquisition` relation (no suffix) for its
   * fulfill links, exactly as the OPDS 1.2 serialization of the same
   * data does. With `showOnlySupportedBooks` at its default of `true`,
   * every loan entry must survive the filter. If they do not, the sync
   * task sees an empty loans feed and deletes every borrowed book.
   *
   * The fixture is the actual loans document returned by the server.
   */

  @Test
  fun testOPDS20LoanFeedWithBareAcquisitionSurvivesFilter() {
    val json =
      String(
        this::class.java
          .getResourceAsStream("/org/nypl/simplified/tests/feeds/opds20/loans-feed-bare-acquisition.json")!!
          .readAllBytes(),
        StandardCharsets.UTF_8,
      )

    val loader = this.createLoader(json, this.opds20ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    val feedData = feed as org.nypl.simplified.feeds.api.FeedData
    Assertions.assertEquals(3, feedData.size)

    for (entry in feedData.entries) {
      val book = entry as FeedEntryBook
      Assertions.assertEquals(
        org.nypl.simplified.feeds.api.FeedAcquisitionRelation.ACQUISITION_GENERIC,
        book.acquisitions[0].relation,
      )
    }
  }

  /**
   * Verify that `application/opds-publication+json` is registered
   * as a known type in `BookFormatSupport`.
   */

  @Test
  fun testOpds20PublicationIsKnownType() {
    val support =
      BookFormatSupport.create(
        BookFormatSupportParameters(
          supportsPDF = false,
          supportsLCP = false,
          supportsAdobeDRM = false,
          supportsBoundless = false,
          supportsAudioBooks = null,
        ),
      )

    val opds20Type: MIMEType = StandardFormatNames.opds20Publication
    val path = listOf(opds20Type, StandardFormatNames.genericEPUBFiles)
    Assertions.assertTrue(support.isSupportedPath(path))
  }

  /**
   * Verify that a path with ONLY `application/opds-publication+json`
   * (no indirect acquisitions) is NOT supported, because it is not
   * a final type.
   */

  @Test
  fun testOpds20PublicationAloneIsNotSupported() {
    val support =
      BookFormatSupport.create(
        BookFormatSupportParameters(
          supportsPDF = false,
          supportsLCP = false,
          supportsAdobeDRM = false,
          supportsBoundless = false,
          supportsAudioBooks = null,
        ),
      )

    val opds20Type: MIMEType = StandardFormatNames.opds20Publication
    Assertions.assertFalse(support.isSupportedPath(listOf(opds20Type)))
  }

  /**
   * An entry with an unsupported final type (e.g. `application/pdf`
   * when PDF support is disabled) must still be filtered out even
   * though the source type is the known OPDS 2.0 wrapper.
   */

  @Test
  fun testOPDS20UnsupportedFinalTypeIsFiltered() {
    val json = this.resource("opds20-unsupported-final-type-is-filtered.json")

    val loader = this.createLoader(json, this.opds20ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertEquals(0, (feed as org.nypl.simplified.feeds.api.FeedData).size)
  }

  private fun resource(
    name: String
  ): String {
    val path = "/org/nypl/simplified/tests/feeds/$name"
    val stream = FeedLoaderOPDS20FilterTest::class.java.getResourceAsStream(path)
    return String(stream!!.readAllBytes(), StandardCharsets.UTF_8)
  }
}
