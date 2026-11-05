package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

/**
 * A feed group.
 */
data class FeedGroup(
  val account: AccountID,
  val groupTitle: String,
  val groupURI: URI,
  val groupEntries: List<FeedEntry>
)
