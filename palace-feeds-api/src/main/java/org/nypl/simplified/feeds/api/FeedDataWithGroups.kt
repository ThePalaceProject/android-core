package org.nypl.simplified.feeds.api

import org.joda.time.DateTime
import java.net.URI

/**
 * A feed with groups. Entries are organized into named groups.
 */
data class FeedDataWithGroups(
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
  override val facetsByGroup: Map<String, List<FeedFacetSingle>>,
  override val facetsOrder: List<FeedFacetSingle>,
  val groups: List<FeedGroup>,
) : Feed {
  override val size: Int
    get() = this.groups.size

  val groupsByID: Map<String, FeedGroup>
    get() = this.groups.associateBy { it.groupTitle }
}
