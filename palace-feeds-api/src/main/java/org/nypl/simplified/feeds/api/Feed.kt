package org.nypl.simplified.feeds.api

import java.net.URI
import java.time.OffsetDateTime

/**
 * A parsed or locally-generated feed.
 */
sealed interface Feed {
  /**
   * The unique identifier of the feed.
   */
  val feedID: String

  /**
   * The search for the feed.
   */
  val feedSearch: FeedSearch?

  /**
   * The title of the feed.
   */
  val feedTitle: String

  /**
   * The last time the feed was updated.
   */
  val feedUpdated: OffsetDateTime

  /**
   * The URI of the feed.
   */
  val feedURI: URI

  /**
   * A link to the terms of service, if any.
   */
  val feedTermsOfService: URI?

  /**
   * A link to the privacy policy, if any.
   */
  val feedPrivacyPolicy: URI?

  /**
   * A link to the about page, if any.
   */
  val feedAbout: URI?

  /**
   * A link to the licenses, if any.
   */
  val feedLicenses: URI?

  /**
   * A link to the next part of the feed, if any.
   */
  val feedNext: URI?

  /**
   * The URI of the authentication document, if any.
   */
  val feedAuthDocument: URI?

  /**
   * The facets, grouped by name.
   */
  val facetsByGroup: Map<String, List<FeedFacetSingle>>

  /**
   * The facets, in declaration order.
   */
  val facetsOrder: List<FeedFacetSingle>

  /**
   * The number of top-level elements in the feed (entries or groups).
   */
  val size: Int
}
