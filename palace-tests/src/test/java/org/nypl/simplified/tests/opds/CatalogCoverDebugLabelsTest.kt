package org.nypl.simplified.tests.opds

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.opds12.OPDS12FeedConverter
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser.Companion.newParser
import org.nypl.simplified.opds.core.OPDSFeedParser.Companion.newParser
import org.nypl.simplified.ui.catalog.CatalogCoverDebugLabels
import java.net.URI

/**
 * Tests for the DRM name derivation used in the debug cover labels.
 *
 * The fixture feed is a real-world feed in which:
 *
 * - The DRM license type (ACSM, LCP license, ...) is never the *final*
 *   content type of an acquisition path; it is the first (or an
 *   intermediate) element, and the final element is the raw book file.
 * - No entry carries a `drm:licensor` element.
 *
 * This is exactly the combination that previously caused DRM-protected
 * books to be labelled as having no DRM: the old code only considered the
 * final content types of the acquisition paths, and the licensor fallback
 * never fired.
 */

class CatalogCoverDebugLabelsTest {
  private fun parseFeed(): OPDSAcquisitionFeed {
    val uri =
      URI.create("https://gorgon.staging.palaceproject.io/a1qa-test/groups/")
    val feedParser =
      newParser(newParser())
    val stream =
      this::class.java.getResourceAsStream("/org/nypl/simplified/tests/opds/a1qa-20260605.xml")
        ?: throw IllegalStateException("Missing fixture a1qa-20260605.xml")
    return feedParser.parse(uri, stream)
  }

  private fun allEntries(feed: OPDSAcquisitionFeed): List<FeedEntryBook> {
    return (feed.feedEntries + feed.feedGroups.values.flatMap { it.entries }).map {
      OPDS12FeedConverter.convertEntry(AccountID.generate(), it)
    }
  }

  /**
   * An Adobe-DRM-protected EPUB. The acquisition path is
   * `application/atom+xml;type=entry;profile=opds-catalog` ->
   * `application/vnd.adobe.adept+xml` -> `application/epub+zip`, so the
   * ACSM type is an intermediate path element, not the final content type.
   * No `drm:licensor` element is present.
   */

  @Test
  fun testAdobeEpub() {
    val entries = this.allEntries(this.parseFeed())
    val entry =
      entries.first { it.title == "Later" }
    assertEquals("Adobe", CatalogCoverDebugLabels.drmNameOf(entry))
  }

  /**
   * An LCP-protected EPUB. The LCP license type is an intermediate path
   * element, not the final content type, and no `drm:licensor` element is
   * present.
   */

  @Test
  fun testLcpEpub() {
    val entries = this.allEntries(this.parseFeed())
    val entry =
      entries.first { it.title == "Tras las huellas de la ciencia" }
    assertEquals("LCP", CatalogCoverDebugLabels.drmNameOf(entry))
  }

  /**
   * An LCP-protected audiobook.
   */

  @Test
  fun testLcpAudiobook() {
    val entries = this.allEntries(this.parseFeed())
    val entry =
      entries.first { it.title == "The Confusion" }
    assertEquals("LCP", CatalogCoverDebugLabels.drmNameOf(entry))
  }

  /**
   * An Overdrive audiobook.
   */

  @Test
  fun testOverdriveAudiobook() {
    val entries = this.allEntries(this.parseFeed())
    val entry =
      entries.first { it.title == "Private Down Under" }
    assertEquals("Overdrive", CatalogCoverDebugLabels.drmNameOf(entry))
  }

  /**
   * A Findaway audiobook.
   */

  @Test
  fun testFindawayAudiobook() {
    val entries = this.allEntries(this.parseFeed())
    val entry =
      entries.first { it.title == "Dune" }
    assertEquals("Findaway", CatalogCoverDebugLabels.drmNameOf(entry))
  }

  /**
   * A DRM-free EPUB must be labelled as having no DRM.
   */

  @Test
  fun testNoDrmEpub() {
    val entries = this.allEntries(this.parseFeed())
    val entry =
      entries.first { it.title == "Vagrant" }
    assertEquals("none", CatalogCoverDebugLabels.drmNameOf(entry))
  }
}
