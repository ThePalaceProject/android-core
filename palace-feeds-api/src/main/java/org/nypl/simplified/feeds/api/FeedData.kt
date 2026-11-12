package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * A feed without groups. Entries are listed directly.
 */
data class FeedData(
  override val feedID: String,
  override val feedSearch: FeedSearch?,
  override val feedTitle: String,
  override val feedUpdated: DateTime,
  override val feedURI: URI,
  override val feedTermsOfService: URI?,
  override val feedPrivacyPolicy: URI?,
  override val feedAbout: URI?,
  override val feedLicenses: URI?,
  override val feedNext: URI?,
  override val feedAuthDocument: URI?,
  override val facetsByGroup: Map<String, List<FeedFacetSingle>>,
  override val facetsOrder: List<FeedFacetSingle>,
  val entries: List<FeedEntry>,
) : Feed {
  override val size: Int
    get() = this.entries.size

  val entriesByID: Map<FeedBookID, FeedEntry>
    get() = this.entries.associateBy { it.bookID }

  fun containsBook(bookID: FeedBookID): Boolean {
    return this.entries.any { it.bookID == bookID }
  }

  companion object {
    /**
     * Construct an empty feed without groups.
     */
    fun empty(
      feedID: String,
      feedSearch: FeedSearch?,
      feedTitle: String,
      feedURI: URI,
      feedFacets: List<FeedFacetSingle>,
      feedFacetGroups: Map<String, List<FeedFacetSingle>>,
    ): FeedData {
      return FeedData(
        feedID = feedID,
        feedSearch = feedSearch,
        feedTitle = feedTitle,
        feedUpdated = DateTime.now(),
        feedURI = feedURI,
        feedTermsOfService = null,
        feedPrivacyPolicy = null,
        feedAbout = null,
        feedLicenses = null,
        feedNext = null,
        feedAuthDocument = null,
        facetsByGroup = feedFacetGroups,
        facetsOrder = feedFacets,
        entries = emptyList(),
      )
    }
  }
}
