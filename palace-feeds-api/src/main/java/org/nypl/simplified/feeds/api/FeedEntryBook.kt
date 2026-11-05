package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs.newFromText
import java.net.URI

/**
 * A book entry in a feed.
 *
 * This type carries the full field surface of a feed entry, regardless of
 * whether it was parsed from OPDS 1.2, OPDS 2.0, or generated locally.
 */
data class FeedEntryBook(
  override val accountID: AccountID,
  /**
   * The entry ID.
   */
  val id: String,
  /**
   * The title.
   */
  val title: String,
  /**
   * The time of the last update.
   */
  val updated: DateTime,
  /**
   * The list of authors.
   */
  val authors: List<String>,
  /**
   * The entry availability.
   */
  val availability: FeedAvailability,
  /**
   * The list of acquisitions.
   */
  val acquisitions: List<FeedAcquisition>,
  /**
   * The list of categories.
   */
  val categories: List<FeedCategory>,
  /**
   * The cover image URI.
   */
  val cover: URI?,
  /**
   * The list of preview acquisitions.
   */
  val previewAcquisitions: List<FeedPreviewAcquisition>,
  /**
   * The annotations URI.
   */
  val annotations: URI?,
  /**
   * The groups this entry belongs to.
   */
  val groups: Set<FeedEntryGroupRef>,
  /**
   * The report issues URI.
   */
  val issues: URI?,
  /**
   * The related feed URI.
   */
  val related: URI?,
  /**
   * The entry publication date.
   */
  val published: DateTime?,
  /**
   * The publisher.
   */
  val publisher: String?,
  /**
   * The distribution.
   */
  val distribution: String,
  /**
   * The summary.
   */
  val summary: String,
  /**
   * The list of narrators.
   */
  val narrators: List<String>,
  /**
   * The thumbnail URI.
   */
  val thumbnail: URI?,
  /**
   * The time tracking URI.
   */
  val timeTrackingUri: URI?,
  /**
   * The alternate URI.
   */
  val alternate: URI?,
  /**
   * The analytics URI.
   */
  val analytics: URI?,
  /**
   * The DRM licensor.
   */
  val licensor: FeedDRMLicensor?,
  /**
   * Duration in seconds.
   */
  val duration: Double?,
  /**
   * The language.
   */
  val language: String?,
  /**
   * The audience.
   */
  val audience: String?,
  /**
   * The series.
   */
  val series: FeedSeries?
) : FeedEntry {
  override val bookID: BookID
    get() = newFromText(this.id)

  /**
   * The authors as a comma-separated string.
   */
  val authorsCommaSeparated: String
    get() = this.authors.joinToString(", ")
}
