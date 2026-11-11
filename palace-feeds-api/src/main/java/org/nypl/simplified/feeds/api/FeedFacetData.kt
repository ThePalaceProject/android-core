package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

/**
 * A facet taken from a parsed feed.
 *
 * This carries the full field surface of a feed facet, regardless of
 * whether it was parsed from OPDS 1.2 or OPDS 2.0.
 */
data class FeedFacetData(
  val accountID: AccountID,
  override val title: String,
  override val isActive: Boolean,
  val uri: URI,
  val group: String,
  val groupType: String?,
) : FeedFacetSingle {
  companion object {
    const val ENTRYPOINT_FACET_GROUP_TYPE = "http://librarysimplified.org/terms/rel/entrypoint"
  }
}
