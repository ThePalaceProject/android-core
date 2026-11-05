package org.nypl.simplified.tests.feeds.opds12

import one.irradia.mime.vanilla.MIMEParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedAvailabilityHeld
import org.nypl.simplified.feeds.api.FeedAvailabilityHeldReady
import org.nypl.simplified.feeds.api.FeedAvailabilityHoldable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoaned
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedAvailabilityRevoked
import org.nypl.simplified.feeds.api.FeedCategory
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedDRMLicensor
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryGroupRef
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition
import org.nypl.simplified.feeds.api.FeedSearchOpen1_1
import org.nypl.simplified.feeds.api.FeedSeries
import org.nypl.simplified.feeds.opds12.OPDS12FeedConverter
import org.nypl.simplified.links.Link.LinkBasic
import org.nypl.simplified.opds.core.DRMLicensor
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.opds.core.OPDSCategory
import org.nypl.simplified.opds.core.OPDSFacet
import org.nypl.simplified.opds.core.OPDSGroup
import org.nypl.simplified.opds.core.OPDSIndirectAcquisition
import org.nypl.simplified.opds.core.OPDSOpenSearch1_1
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition
import org.nypl.simplified.opds.core.OPDSSeries
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.net.URI
import java.util.UUID

/**
 * Tests for the OPDS 1.2 to Feed* type converter.
 *
 * These tests focus on verifying that all field values, especially
 * date/time values (Joda Time DateTime), are preserved correctly
 * through the conversion from OPDS types to Feed types.
 */
class OPDS12FeedConverterTest {

  private val accountID: AccountID =
    AccountID(UUID.randomUUID())

  /*
   * Fixed date/time values used throughout the tests.
   *
   * Joda Time DateTime equality is based on the instant (millis from epoch),
   * NOT on the timezone or fields. Two DateTime objects with the same instant
   * are equal regardless of their zone. The OPDS parser produces UTC-zoned
   * DateTimes (via ISODateTimeFormat.dateTimeParser().withZoneUTC()).
   */
  private val date20240115T103000Z: DateTime =
    DateTime(2024, 1, 15, 10, 30, 0, DateTimeZone.UTC)

  private val date20240220T144530Z: DateTime =
    DateTime(2024, 2, 20, 14, 45, 30, DateTimeZone.UTC)

  private val date20240325T080000Z: DateTime =
    DateTime(2024, 3, 25, 8, 0, 0, DateTimeZone.UTC)

  private val date20240410T160000Z: DateTime =
    DateTime(2024, 4, 10, 16, 0, 0, DateTimeZone.UTC)

  @Test
  fun testFeedBasics() {
    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-id-1",
        date20240115T103000Z,
        "Test Feed"
      )
        .setNextOption(URI.create("http://example.com/feed?page=2"))
        .setAboutOption(URI.create("http://example.com/about"))
        .setTermsOfServiceOption(URI.create("http://example.com/tos"))
        .setPrivacyPolicyOption(URI.create("http://example.com/privacy"))
        .build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    Assertions.assertTrue(result is FeedData)
    val data = result as FeedData

    assertEquals("feed-id-1", data.feedID)
    assertEquals("Test Feed", data.feedTitle)
    // DateTime equality in Joda is instant-based
    assertEquals(date20240115T103000Z, data.feedUpdated)
    assertEquals(URI.create("http://example.com/feed"), data.feedURI)
    assertEquals(URI.create("http://example.com/feed?page=2"), data.feedNext)
    assertEquals(URI.create("http://example.com/about"), data.feedAbout)
    assertEquals(URI.create("http://example.com/tos"), data.feedTermsOfService)
    assertEquals(URI.create("http://example.com/privacy"), data.feedPrivacyPolicy)
    assertNull(data.feedLicenses)
    assertNull(data.feedSearch)
    assertEquals(0, data.entries.size)
  }

  @Test
  fun testFeedWithSearch() {
    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-id-2",
        date20240115T103000Z,
        "Searchable Feed"
      ).build()

    val search =
      OPDSOpenSearch1_1("http://example.com/search?q={searchTerms}")

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = search
      )

    val data = result as FeedData
    val feedSearch = data.feedSearch
    Assertions.assertTrue(feedSearch is FeedSearchOpen1_1)

    val searchOpen = feedSearch as FeedSearchOpen1_1
    val queryUri = searchOpen.getQueryURIForTerms("hello world")
    assertEquals("http://example.com/search?q=hello+world", queryUri.toString())
  }

  @Test
  fun testEntryAllFields() {
    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:1234567890",
        title = "Test Book",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).apply {
        authors.add("Author One")
        authors.add("Author Two")
        published = date20240220T144530Z
        publisher = "Test Publisher"
        distribution = "Wide"
        summary = "A test book summary"
        narrators.add("Narrator One")
        cover = URI.create("http://example.com/cover.jpg")
        thumbnail = URI.create("http://example.com/thumb.jpg")
        annotations = URI.create("http://example.com/annotations")
        issues = URI.create("http://example.com/issues")
        related = URI.create("http://example.com/related")
        alternate = URI.create("http://example.com/alternate")
        analytics = URI.create("http://example.com/analytics")
        timeTrackingUri = URI.create("http://example.com/tracking")
        categories.add(OPDSCategory("Fiction", "http://schema.org/genre", null))
        categories.add(OPDSCategory("Adult", "http://schema.org/audience", "For Adults"))
        groups.add(Pair("Group One", URI.create("http://example.com/group1")))
        licensor = DRMLicensor("Adobe", "token123", "http://example.com/dm")
        series.add(OPDSSeries("Test Series", 3, URI.create("http://example.com/series")))
        previewAcquisitions.add(
          OPDSPreviewAcquisition(
            URI.create("http://example.com/preview"),
            MIMEParser.parseRaisingException("application/epub+zip")
          )
        )
        acquisitionList.add(
          OPDSAcquisition(
            relation = OPDSAcquisition.Relation.ACQUISITION_BORROW,
            uri = LinkBasic(URI.create("http://example.com/borrow")),
            type = MIMEParser.parseRaisingException("application/epub+zip"),
            indirectAcquisitions = emptyList(),
            properties = mapOf("key" to "value")
          )
        )
      }.build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-id-3",
        date20240115T103000Z,
        "Entry Test"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    assertEquals(1, data.entries.size)

    val converted = data.entries[0] as FeedEntryBook

    // Identity fields
    assertEquals("urn:isbn:1234567890", converted.id)
    assertEquals("Test Book", converted.title)
    assertEquals(this.accountID, converted.accountID)

    // Date/time fields - Joda DateTime equality is instant-based
    assertEquals(date20240115T103000Z, converted.updated)
    assertEquals(date20240220T144530Z, converted.published)

    // String fields
    assertEquals(listOf("Author One", "Author Two"), converted.authors)
    assertEquals("Test Publisher", converted.publisher)
    assertEquals("Wide", converted.distribution)
    assertEquals("A test book summary", converted.summary)
    assertEquals(listOf("Narrator One"), converted.narrators)

    // URI fields
    assertEquals(URI.create("http://example.com/cover.jpg"), converted.cover)
    assertEquals(URI.create("http://example.com/thumb.jpg"), converted.thumbnail)
    assertEquals(URI.create("http://example.com/annotations"), converted.annotations)
    assertEquals(URI.create("http://example.com/issues"), converted.issues)
    assertEquals(URI.create("http://example.com/related"), converted.related)
    assertEquals(URI.create("http://example.com/alternate"), converted.alternate)
    assertEquals(URI.create("http://example.com/analytics"), converted.analytics)
    assertEquals(URI.create("http://example.com/tracking"), converted.timeTrackingUri)

    // Nested types
    assertEquals(
      listOf(
        FeedCategory("Fiction", "http://schema.org/genre", null),
        FeedCategory("Adult", "http://schema.org/audience", "For Adults")
      ),
      converted.categories
    )

    assertEquals(
      setOf(FeedEntryGroupRef("Group One", URI.create("http://example.com/group1"))),
      converted.groups
    )

    assertEquals(
      FeedDRMLicensor("Adobe", "token123", "http://example.com/dm"),
      converted.licensor
    )

    assertEquals(
      FeedSeries("Test Series", 3, URI.create("http://example.com/series")),
      converted.series
    )

    assertEquals(
      listOf(
        FeedPreviewAcquisition(
          URI.create("http://example.com/preview"),
          MIMEParser.parseRaisingException("application/epub+zip")
        )
      ),
      converted.previewAcquisitions
    )

    // Availability
    assertEquals(FeedAvailabilityLoanable, converted.availability)

    // Acquisitions
    assertEquals(1, converted.acquisitions.size)
    val acq = converted.acquisitions[0]
    assertEquals(FeedAcquisitionRelation.ACQUISITION_BORROW, acq.relation)
    assertEquals("http://example.com/borrow", acq.uri.uri.toString())
    assertEquals(mapOf("key" to "value"), acq.properties)
  }

  @Test
  fun testEntryNullOptionalFields() {
    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:0000000000",
        title = "Minimal Book",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-id-4",
        date20240115T103000Z,
        "Minimal"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    // All optional fields should be null/empty
    assertNull(converted.published)
    assertNull(converted.cover)
    assertNull(converted.thumbnail)
    assertNull(converted.annotations)
    assertNull(converted.issues)
    assertNull(converted.related)
    assertNull(converted.alternate)
    assertNull(converted.analytics)
    assertNull(converted.timeTrackingUri)
    assertNull(converted.publisher)
    assertNull(converted.licensor)
    assertNull(converted.series)
    assertTrue(converted.authors.isEmpty())
    assertTrue(converted.categories.isEmpty())
    assertTrue(converted.acquisitions.isEmpty())
    assertTrue(converted.previewAcquisitions.isEmpty())
    assertTrue(converted.groups.isEmpty())
    assertTrue(converted.narrators.isEmpty())
  }

  @Test
  fun testAvailabilityLoaned() {
    val revokeUri = URI.create("http://example.com/revoke")
    val availability =
      OPDSAvailabilityLoaned.get(
        startDate = date20240115T103000Z,
        endDate = date20240325T080000Z,
        revoke = revokeUri
      )

    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:111",
        title = "Loaned Book",
        updated = date20240115T103000Z,
        availability = availability
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-loaned",
        date20240115T103000Z,
        "Loaned"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    assertEquals(
      FeedAvailabilityLoaned(
        startDate = date20240115T103000Z,
        endDate = date20240325T080000Z,
        revoke = revokeUri
      ),
      converted.availability
    )
  }

  @Test
  fun testAvailabilityHeld() {
    val revokeUri = URI.create("http://example.com/revoke")
    val availability =
      OPDSAvailabilityHeld.get(
        startDate = date20240115T103000Z,
        position = 5,
        endDate = date20240410T160000Z,
        revoke = revokeUri
      )

    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:222",
        title = "Held Book",
        updated = date20240115T103000Z,
        availability = availability
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-held",
        date20240115T103000Z,
        "Held"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    assertEquals(
      FeedAvailabilityHeld(
        startDate = date20240115T103000Z,
        position = 5,
        endDate = date20240410T160000Z,
        revoke = revokeUri
      ),
      converted.availability
    )
  }

  @Test
  fun testAvailabilityHeldReady() {
    val revokeUri = URI.create("http://example.com/revoke")
    val availability =
      OPDSAvailabilityHeldReady.get(
        endDate = date20240325T080000Z,
        revoke = revokeUri
      )

    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:333",
        title = "HeldReady Book",
        updated = date20240115T103000Z,
        availability = availability
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-heldready",
        date20240115T103000Z,
        "HeldReady"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    assertEquals(
      FeedAvailabilityHeldReady(
        endDate = date20240325T080000Z,
        revoke = revokeUri
      ),
      converted.availability
    )
  }

  @Test
  fun testAvailabilityOpenAccess() {
    val revokeUri = URI.create("http://example.com/revoke")
    val availability =
      OPDSAvailabilityOpenAccess.get(
        revoke = revokeUri
      )

    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:444",
        title = "OpenAccess Book",
        updated = date20240115T103000Z,
        availability = availability
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-openaccess",
        date20240115T103000Z,
        "OpenAccess"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    assertEquals(
      FeedAvailabilityOpenAccess(
        endDate = null,
        revoke = revokeUri
      ),
      converted.availability
    )
  }

  @Test
  fun testAvailabilityRevoked() {
    val revokeUri = URI.create("http://example.com/revoke")
    val availability =
      OPDSAvailabilityRevoked.get(
        revoke = revokeUri
      )

    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:555",
        title = "Revoked Book",
        updated = date20240115T103000Z,
        availability = availability
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-revoked",
        date20240115T103000Z,
        "Revoked"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    assertEquals(
      FeedAvailabilityRevoked(
        endDate = null,
        revoke = revokeUri
      ),
      converted.availability
    )
  }

  @Test
  fun testAvailabilityHoldable() {
    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:666",
        title = "Holdable Book",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityHoldable.get()
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-holdable",
        date20240115T103000Z,
        "Holdable"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook
    assertEquals(FeedAvailabilityHoldable, converted.availability)
  }

  @Test
  fun testFeedWithGroups() {
    val entry1 =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:777",
        title = "Book in Group 1",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).apply {
        groups.add(Pair("Group One", URI.create("http://example.com/g1")))
      }.build()

    val entry2 =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:888",
        title = "Book in Group 2",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).apply {
        groups.add(Pair("Group Two", URI.create("http://example.com/g2")))
      }.build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-groups",
        date20240115T103000Z,
        "Grouped Feed"
      ).apply {
        addEntry(entry1)
        addEntry(entry2)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    // The OPDSAcquisitionFeed builder distributes entries into groups
    // based on their group references
    Assertions.assertTrue(result is FeedDataWithGroups, "Expected FeedDataWithGroups but got ${result::class}")
    val data = result as FeedDataWithGroups
    assertEquals(2, data.groups.size)

    // Verify group entries have correct dates
    for (group in data.groups) {
      for (e in group.groupEntries) {
        val book = e as FeedEntryBook
        assertEquals(date20240115T103000Z, book.updated)
      }
    }
  }

  @Test
  fun testFacets() {
    val facet1 =
      OPDSFacet(
        isActive = false,
        uri = URI.create("http://example.com/feed?sort=title"),
        group = "Sort by",
        title = "Title",
        groupType = null
      )

    val facet2 =
      OPDSFacet(
        isActive = true,
        uri = URI.create("http://example.com/feed?sort=author"),
        group = "Sort by",
        title = "Author",
        groupType = null
      )

    val facet3 =
      OPDSFacet(
        isActive = false,
        uri = URI.create("http://example.com/feed?genre=fiction"),
        group = "Genre",
        title = "Fiction",
        groupType = "http://librarysimplified.org/terms/rel/entrypoint"
      )

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-facets",
        date20240115T103000Z,
        "Facet Feed"
      ).apply {
        addFacet(facet1)
        addFacet(facet2)
        addFacet(facet3)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    assertEquals(3, data.facetsOrder.size)

    // Verify facet data
    val f0 = data.facetsOrder[0] as FeedFacetData
    assertEquals("Title", f0.title)
    assertEquals(false, f0.isActive)
    assertEquals("Sort by", f0.group)
    assertEquals(URI.create("http://example.com/feed?sort=title"), f0.uri)
    assertNull(f0.groupType)
    assertEquals(this.accountID, f0.accountID)

    val f1 = data.facetsOrder[1] as FeedFacetData
    assertEquals("Author", f1.title)
    assertEquals(true, f1.isActive)

    val f2 = data.facetsOrder[2] as FeedFacetData
    assertEquals("Fiction", f2.title)
    assertEquals("http://librarysimplified.org/terms/rel/entrypoint", f2.groupType)
  }

  @Test
  fun testEntryFilter() {
    val entry1 =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:aaa",
        title = "Keep Me",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).build()

    val entry2 =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:bbb",
        title = "Filter Me Out",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-filter",
        date20240115T103000Z,
        "Filter Test"
      ).apply {
        addEntry(entry1)
        addEntry(entry2)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { it.id == "urn:isbn:aaa" },
        search = null
      )

    val data = result as FeedData
    assertEquals(1, data.entries.size)
    assertEquals("Keep Me", (data.entries[0] as FeedEntryBook).title)
  }

  @Test
  fun testDateTimeEqualityAcrossZones() {
    /*
     * Verify that Joda DateTime equality works correctly across different
     * timezone representations of the same instant. This is the key
     * property that makes the converter work: the OPDS parser produces
     * UTC-zoned DateTimes, and the converted Feed types hold the same
     * references, so equality is trivially preserved.
     */
    val utc = DateTime(2024, 6, 15, 12, 0, 0, DateTimeZone.UTC)
    val ny = utc.withZone(DateTimeZone.forID("America/New_York"))

    // Same instant, different zone - Joda considers these equal
    assertEquals(utc, ny)
    assertEquals(utc.millis, ny.millis)

    // The converter passes the DateTime reference through directly,
    // so the zone is preserved
    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:zone-test",
        title = "Zone Test",
        updated = utc,
        availability = OPDSAvailabilityLoanable.get()
      ).build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-zone",
        utc,
        "Zone"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    // The DateTime reference is passed through unchanged
    assertEquals(utc, converted.updated)
    assertEquals(DateTimeZone.UTC, converted.updated.zone)
  }

  @Test
  fun testDateTimeNullHandling() {
    /*
     * Verify that null dates are handled correctly. The OPDS parser
     * produces null for missing optional date fields (published,
     * availability start/end dates).
     */
    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:null-dates",
        title = "Null Dates",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).build()

    // published is null by default
    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-null-dates",
        date20240115T103000Z,
        "Null Dates"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    // updated is always present (required field)
    assertNotNull(converted.updated)
    assertEquals(date20240115T103000Z, converted.updated)

    // published is null
    assertNull(converted.published)

    // Availability endDate is null for Loanable
    val availability = converted.availability
    Assertions.assertTrue(availability is FeedAvailabilityLoanable)
    assertNull(availability.endDate)
  }

  @Test
  fun testAcquisitionWithIndirects() {
    val indirect1 =
      OPDSIndirectAcquisition(
        type = MIMEParser.parseRaisingException("application/atomicpath+json"),
        indirectAcquisitions =
          listOf(
            OPDSIndirectAcquisition(
              type = MIMEParser.parseRaisingException("application/epub+zip"),
              indirectAcquisitions = emptyList(),
              properties = emptyMap()
            )
          ),
        properties = mapOf("depth" to "1")
      )

    val acquisition =
      OPDSAcquisition(
        relation = OPDSAcquisition.Relation.ACQUISITION_BORROW,
        uri = LinkBasic(URI.create("http://example.com/atomicpath")),
        type = MIMEParser.parseRaisingException("application/atomicpath+json"),
        indirectAcquisitions = listOf(indirect1),
        properties = emptyMap()
      )

    val entry =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:indirect",
        title = "Indirect Acq",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).apply {
        acquisitionList.add(acquisition)
      }.build()

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-indirect",
        date20240115T103000Z,
        "Indirect"
      ).apply {
        addEntry(entry)
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    val converted = data.entries[0] as FeedEntryBook

    assertEquals(1, converted.acquisitions.size)
    val acq = converted.acquisitions[0]
    assertEquals(1, acq.indirectAcquisitions.size)

    val indirect = acq.indirectAcquisitions[0]
    assertEquals("application", indirect.type.type)
    assertEquals("atomicpath+json", indirect.type.subtype)
    assertEquals(mapOf("depth" to "1"), indirect.properties)
    assertEquals(1, indirect.indirectAcquisitions.size)

    val leaf = indirect.indirectAcquisitions[0]
    assertEquals("application", leaf.type.type)
    assertEquals("epub+zip", leaf.type.subtype)
    assertTrue(leaf.indirectAcquisitions.isEmpty())
  }

  @Test
  fun testFeedEmpty() {
    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/empty"),
        "feed-empty",
        date20240115T103000Z,
        "Empty Feed"
      ).build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    assertEquals(0, data.entries.size)
    assertEquals(0, data.size)
    assertTrue(data.entriesByID.isEmpty())
  }

  @Test
  fun testMultipleEntriesOrderPreserved() {
    val entries = (1..5).map { i ->
      OPDSAcquisitionFeedEntry.newBuilder(
        id = "urn:isbn:order-$i",
        title = "Book $i",
        updated = date20240115T103000Z,
        availability = OPDSAvailabilityLoanable.get()
      ).build()
    }

    val feed =
      OPDSAcquisitionFeed.newBuilder(
        URI.create("http://example.com/feed"),
        "feed-order",
        date20240115T103000Z,
        "Order Test"
      ).apply {
        entries.forEach { addEntry(it) }
      }.build()

    val result =
      OPDS12FeedConverter.convert(
        accountID = this.accountID,
        feed = feed,
        filter = { true },
        search = null
      )

    val data = result as FeedData
    assertEquals(5, data.entries.size)
    for (i in 0 until 5) {
      val e = data.entries[i] as FeedEntryBook
      assertEquals("urn:isbn:order-${i + 1}", e.id)
      assertEquals("Book ${i + 1}", e.title)
    }
  }
}
