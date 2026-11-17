package org.nypl.simplified.tests.feeds.opds20

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedAvailabilityHeldReady
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoaned
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.opds20.OPDS20FeedConverter

/**
 * Tests for the OPDS 2.0 feed converter using the Palace WebPub parser.
 *
 * These tests run on the plain JVM — no Robolectric is required because
 * the WebPub parser is a pure Jackson-based JSON deserializer.
 */
class OPDS20FeedConverterTest {

  private val accountID: AccountID =
    AccountID(java.util.UUID.randomUUID())

  private val feedURI: URI =
    URI("https://example.com/feed.json")

  /**
   * Parse the given JSON string and convert to a PalaceFeed.
   */
  private fun parseAndConvert(json: String): Feed {
    return OPDS20FeedConverter.convert(
      accountID = this.accountID,
      feed = OPDS20FeedConverter.parse(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))),
      feedURI = this.feedURI
    )
  }

  @Test
  fun testEmptyFeed() {
    val json = this.resource("empty-feed.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals("Test Feed", feedData.feedTitle)
    assertEquals(this.feedURI, feedData.feedURI)
    assertEquals(0, feedData.size)
  }

  @Test
  fun testFeedWithPublication() {
    val json = this.resource("feed-with-publication.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(1, feedData.size)

    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals("Test Book", entry.title)
    assertEquals("urn:isbn:1234567890", entry.id)
    assertEquals(listOf("Author One"), entry.authors)
    assertEquals(1, entry.acquisitions.size)
  }

  @Test
  fun testFeedWithFacets() {
    val json = this.resource("feed-with-facets.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(1, feedData.facetsOrder.size)

    val facet = feedData.facetsOrder[0] as FeedFacetData
    assertEquals("Genre", facet.title)
    assertTrue(facet.isActive)
  }

  @Test
  fun testFeedWithGroups() {
    val json = this.resource("feed-with-groups.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedDataWithGroups)
    val feedData = converted as FeedDataWithGroups
    assertEquals(1, feedData.groups.size)
    assertEquals("New Releases", feedData.groups[0].groupTitle)
    assertEquals(1, feedData.groups[0].groupEntries.size)
    val groupEntry = feedData.groups[0].groupEntries[0] as FeedEntryBook
    assertEquals("Grouped Book", groupEntry.title)
  }

  @Test
  fun testFeedMetadata() {
    val json = this.resource("feed-metadata.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals("Test Feed", feedData.feedTitle)
    assertNotNull(feedData.feedUpdated)
  }

  @Test
  fun testEntryWithSubjects() {
    val json = this.resource("entry-with-subjects.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals(1, entry.categories.size)
    assertEquals("Fiction", entry.categories[0].label)
  }

  @Test
  fun testEntryWithMultipleAuthors() {
    val json = this.resource("entry-with-multiple-authors.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals(listOf("Author One", "Author Two", "Author Three"), entry.authors)
    assertEquals("Author One, Author Two, Author Three", entry.authorsCommaSeparated)
  }

  @Test
  fun testEntryWithLanguageAndPublisher() {
    val json = this.resource("entry-with-language-and-publisher.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals("en", entry.language)
    assertEquals("Test Publisher", entry.publisher)
    assertEquals("A test book description", entry.summary)
    assertEquals(3600.0, entry.duration!!, 0.001)
  }

  @Test
  fun testFeedNextLink() {
    val json = this.resource("feed-next-link.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/feed.json?page=2"), feedData.feedNext)
  }

  @Test
  fun testOrderPreserved() {
    val json = this.resource("order-preserved.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(3, feedData.size)
    assertEquals("First Book", (feedData.entries[0] as FeedEntryBook).title)
    assertEquals("Second Book", (feedData.entries[1] as FeedEntryBook).title)
    assertEquals("Third Book", (feedData.entries[2] as FeedEntryBook).title)
  }

  @Test
  fun testFeedSearch() {
    val json = this.resource("feed-search.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertNotNull(feedData.feedSearch)
  }

  @Test
  fun testFeedTermsOfServiceAndPrivacyPolicyAndAbout() {
    val json = this.resource("feed-terms-of-service-and-privacy-policy-and-about.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/tos"), feedData.feedTermsOfService)
    assertEquals(URI("https://example.com/privacy"), feedData.feedPrivacyPolicy)
    assertEquals(URI("https://example.com/about"), feedData.feedAbout)
  }

  @Test
  fun testEntryPreviewAcquisitions() {
    val json = this.resource("entry-preview-acquisitions.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(1, entry.acquisitions.size)
    assertEquals(1, entry.previewAcquisitions.size)
    assertEquals(URI("https://example.com/book-sample.pdf"), entry.previewAcquisitions[0].uri)
  }

  @Test
  fun testEntryAnnotationsAndIssuesAndRelated() {
    val json = this.resource("entry-annotations-and-issues-and-related.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(URI("https://example.com/annotations"), entry.annotations)
    assertEquals(URI("https://example.com/issues"), entry.issues)
    assertEquals(URI("https://example.com/related"), entry.related)
  }

  @Test
  fun testEntryAlternateAndAnalyticsAndTimeTracking() {
    val json = this.resource("entry-alternate-and-analytics-and-time-tracking.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(URI("https://example.com/alternate"), entry.alternate)
    assertEquals(URI("https://example.com/analytics"), entry.analytics)
    assertEquals(URI("https://example.com/time-tracking"), entry.timeTrackingUri)
  }

  @Test
  fun testEntryGroups() {
    val json = this.resource("entry-groups.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(2, entry.groups.size)
    val groupNames = entry.groups.map { it.name }.toSet()
    assertEquals(setOf("Sci-Fi", "Favorites"), groupNames)
  }

  @Test
  fun testEntryCoverAndThumbnail() {
    val json = this.resource("entry-cover-and-thumbnail.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(URI("https://example.com/cover.jpg"), entry.cover)
    assertEquals(URI("https://example.com/thumb.jpg"), entry.thumbnail)
  }

  @Test
  fun testEntryNarratorsAndSeries() {
    val json = this.resource("entry-narrators-and-series.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(listOf("Narrator One"), entry.narrators)
    assertNotNull(entry.series)
    assertEquals("Dune", entry.series!!.name)
    assertEquals(1, entry.series!!.position)
  }

  @Test
  fun testEntryAvailabilityFromProperties() {
    val json = this.resource("entry-availability-from-properties.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityLoanable)
  }

  @Test
  fun testEntryOpenAccessAvailability() {
    val json = this.resource("entry-open-access-availability.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityOpenAccess)
  }

  @Test
  fun testFeedLicenses() {
    val json = this.resource("feed-licenses.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/licenses"), feedData.feedLicenses)
  }

  @Test
  fun testEntryIndirectAcquisitions() {
    val json = this.resource("entry-indirect-acquisitions.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(1, entry.acquisitions.size)
    assertEquals(1, entry.acquisitions[0].indirectAcquisitions.size)
    assertEquals(
      "application/vnd.adobe.adept+xml",
      entry.acquisitions[0].indirectAcquisitions[0].type.fullType
    )
    assertEquals(1, entry.acquisitions[0].indirectAcquisitions[0].indirectAcquisitions.size)
    assertEquals(
      "application/vnd.readium.lcp.licenser+json",
      entry.acquisitions[0].indirectAcquisitions[0].indirectAcquisitions[0].type.fullType
    )
  }

  @Test
  fun testEntryDRMLicensor() {
    val json = this.resource("entry-drm-licensor.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertNotNull(entry.licensor)
    assertEquals("adobe", entry.licensor!!.vendor)
    assertEquals("test-token", entry.licensor!!.clientToken)
    assertEquals("https://example.com/devices", entry.licensor!!.deviceManager)
  }

  @Test
  fun testAvailabilityReserved() {
    val json = this.resource("availability-reserved.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is org.nypl.simplified.feeds.api.FeedAvailabilityHeld)
    val held = entry.availability as org.nypl.simplified.feeds.api.FeedAvailabilityHeld
    assertNotNull(held.startDate)
    assertNotNull(held.endDate)
  }

  @Test
  fun testAvailabilityUnavailable() {
    val json = this.resource("availability-unavailable.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is org.nypl.simplified.feeds.api.FeedAvailabilityHoldable)
  }

  /**
   * A hold that has reached the head of the queue keeps its borrow link and
   * reports `state: "ready"` (see
   * https://drafts.opds.io/best-practices/borrowing.html). This must be
   * interpreted as a hold, not as a loan.
   */
  @Test
  fun testAvailabilityReadyHold() {
    val json = this.resource("availability-ready-hold.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityHeldReady)
    val heldReady = entry.availability as FeedAvailabilityHeldReady
    assertEquals(OffsetDateTime.parse("2024-02-04T14:46:46.889Z"), heldReady.endDate)
  }

  /**
   * A loaned book reports its availability on a download (fulfill) link
   * rather than a borrow link. The server in question marks loans with
   * `state: "ready"` and a `since` date, accompanied by a revoke link; the
   * best-practices document uses `state: "available"` for the same purpose.
   * Both must convert to `FeedAvailabilityLoaned` — any other state
   * (in particular `FeedAvailabilityHeldReady`) makes `BookStatus` report
   * the book as held, and `ProfileFeedTask` then hides the book from the
   * profile feed.
   */
  @Test
  fun testAvailabilityReadyLoan() {
    val json = this.resource("availability-ready-loan.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityLoaned)
    val loaned = entry.availability as FeedAvailabilityLoaned
    assertEquals(OffsetDateTime.parse("2024-01-12T17:46:56Z"), loaned.startDate)
    assertEquals(URI("https://example.com/loans/1/revoke"), loaned.revoke)
  }

  @Test
  fun testAvailabilityAvailableLoan() {
    val json = this.resource("availability-available-loan.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityLoaned)
    val loaned = entry.availability as FeedAvailabilityLoaned
    assertEquals(OffsetDateTime.parse("2024-01-02T14:46:46.889Z"), loaned.startDate)
    assertEquals(OffsetDateTime.parse("2024-02-01T14:46:46.889Z"), loaned.endDate)
    assertEquals(URI("https://example.com/loans/2/revoke"), loaned.revoke)
  }

  /**
   * The actual loans document returned by the server: every loaned book is
   * marked with `state: "ready"` on its fulfill link, with a `since` date
   * and a revoke link. Every entry must convert to
   * `FeedAvailabilityLoaned`.
   */
  @Test
  fun testLoanFeedEntriesAreLoaned() {
    val json = this.resource("loans-feed-bare-acquisition.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(3, feedData.size)

    for (entry in feedData.entries) {
      val book = entry as FeedEntryBook
      assertTrue(book.id, book.availability is FeedAvailabilityLoaned)
      val loaned = book.availability as FeedAvailabilityLoaned
      assertNotNull(book.id, loaned.startDate)
      assertNotNull(book.id, loaned.revoke)
    }
  }

  /**
   * Regression test: the server's loans feed is an OPDS 2.0 feed whose
   * publications use the bare `http://opds-spec.org/acquisition` relation
   * (no suffix) for their fulfill links. This is the same data as the
   * OPDS 1.2 loans feed, where the bare relation is parsed as
   * `ACQUISITION_GENERIC`.
   *
   * The converter must not drop these links: every loan entry must come
   * out with a single `ACQUISITION_GENERIC` acquisition. If the
   * acquisitions are lost, the `showOnlySupportedBooks` filter in
   * `FeedLoader` strips every entry, `BookSyncTask` sees an empty feed,
   * and deletes every borrowed book from the database.
   *
   * The fixture is the actual loans document returned by the server.
   */
  @Test
  fun testLoanFeedBareAcquisitionRelation() {
    val json = this.resource("loans-feed-bare-acquisition.json")

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(3, feedData.size)

    val expectedIds = setOf(
      "urn:uuid:66a9cf92-a05c-448c-9205-8f3875caca7b",
      "urn:uuid:6b870f80-5ac9-4f97-a4d9-2c62a3a5cf8b",
      "urn:uuid:656de7cb-dfad-4438-891b-6f2cfe78b8d6",
    )

    val seenIds = mutableSetOf<String>()
    for (entry in feedData.entries) {
      val book = entry as FeedEntryBook
      seenIds.add(book.id)

      assertEquals(1, book.acquisitions.size)
      val acquisition = book.acquisitions[0]
      assertEquals(FeedAcquisitionRelation.ACQUISITION_GENERIC, acquisition.relation)
      assertEquals("application/epub+zip", acquisition.type.fullType)
    }

    assertEquals(expectedIds, seenIds)
  }

  private fun resource(
    name: String
  ): String {
    val path = "/org/nypl/simplified/tests/feeds/opds20/$name"
    val stream = OPDS20FeedConverterTest::class.java.getResourceAsStream(path)
    return String(stream!!.readAllBytes(), StandardCharsets.UTF_8)
  }
}
