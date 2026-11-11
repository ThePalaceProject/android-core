package org.nypl.simplified.tests.feeds

import one.irradia.mime.api.MIMEType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.FeedFetchResult
import org.nypl.simplified.feeds.FeedFetchType
import org.nypl.simplified.feeds.FeedLoader
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Tests for the [FeedLoader] content-type dispatch logic.
 *
 * The loader must parse the feed based on the response [Content-Type]
 * header, not on the [FeedLoader.preferOPDS20] flag. The flag only
 * controls the [Accept] header sent to the server. These tests verify
 * that the loader correctly handles all four combinations of the flag
 * and the response content type.
 */

class FeedLoaderContentTypeDispatchTest {

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

  private val opds12ContentType: MIMEType =
    MIMEType("application", "atom+xml", mapOf("profile" to "opds-catalog"))

  private val opds20ContentType: MIMEType =
    MIMEType("application", "opds+json", mapOf())

  private fun createLoader(
    body: String,
    contentType: MIMEType?,
  ): FeedLoader =
    FeedLoader(
      bookFormatSupport =
        BookFormatSupport.create(
          BookFormatSupportParameters(
            supportsPDF = false,
            supportsLCP = false,
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

  private fun opds12Body(): String =
    javaClass.getResource("feed-opds12-dispatch.xml")!!
      .readText(StandardCharsets.UTF_8)

  private fun opds20Body(): String =
    javaClass.getResource("feed-opds20-dispatch.json")!!
      .readText(StandardCharsets.UTF_8)

  /**
   * preferOPDS20=true, server responds with OPDS 2.0 content type.
   * Expected: parsed as OPDS 2.0.
   */

  @Test
  fun testPreferOPDS20_opds20Response() {
    val loader = this.createLoader(this.opds20Body(), this.opds20ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertTrue(feed is FeedData)
    Assertions.assertEquals(1, (feed as FeedData).size)
    Assertions.assertEquals("Dispatch Test Book", feed.entries[0].let {
      (it as org.nypl.simplified.feeds.api.FeedEntryBook).title
    })
  }

  /**
   * preferOPDS20=true, server responds with OPDS 1.2 content type.
   * Expected: falls back to OPDS 1.2 parsing.
   */

  @Test
  fun testPreferOPDS20_opds12Response_fallback() {
    val loader = this.createLoader(this.opds12Body(), this.opds12ContentType)
    loader.preferOPDS20 = true

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertTrue(feed is FeedData)
    Assertions.assertEquals(1, (feed as FeedData).size)
    Assertions.assertEquals("Dispatch Test Book", feed.entries[0].let {
      (it as org.nypl.simplified.feeds.api.FeedEntryBook).title
    })
  }

  /**
   * preferOPDS20=false, server responds with OPDS 2.0 content type.
   * Expected: parsed as OPDS 2.0 (server ignored the flag).
   */

  @Test
  fun testPreferOPDS12_opds20Response() {
    val loader = this.createLoader(this.opds20Body(), this.opds20ContentType)
    loader.preferOPDS20 = false

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertTrue(feed is FeedData)
    Assertions.assertEquals(1, (feed as FeedData).size)
    Assertions.assertEquals("Dispatch Test Book", feed.entries[0].let {
      (it as org.nypl.simplified.feeds.api.FeedEntryBook).title
    })
  }

  /**
   * preferOPDS20=false, server responds with OPDS 1.2 content type.
   * Expected: parsed as OPDS 1.2 (normal case).
   */

  @Test
  fun testPreferOPDS12_opds12Response() {
    val loader = this.createLoader(this.opds12Body(), this.opds12ContentType)
    loader.preferOPDS20 = false

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertTrue(feed is FeedData)
    Assertions.assertEquals(1, (feed as FeedData).size)
    Assertions.assertEquals("Dispatch Test Book", feed.entries[0].let {
      (it as org.nypl.simplified.feeds.api.FeedEntryBook).title
    })
  }

  /**
   * preferOPDS20=false, server responds with null content type.
   * Expected: falls back to OPDS 1.2 parsing (the existing default).
   */

  @Test
  fun testPreferOPDS12_nullContentType_fallbackToOPDS12() {
    val loader = this.createLoader(this.opds12Body(), null)
    loader.preferOPDS20 = false

    val result =
      loader
        .fetchURI(accountID = this.accountID, uri = this.testURI, credentials = null, method = "GET")
        .get()

    Assertions.assertTrue(result is FeedLoaderResult.FeedLoaderSuccess)
    val feed = (result as FeedLoaderResult.FeedLoaderSuccess).feed
    Assertions.assertTrue(feed is FeedData)
    Assertions.assertEquals(1, (feed as FeedData).size)
  }
}
