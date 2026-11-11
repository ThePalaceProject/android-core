package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID

/**
 * A filtering facet for a specific account (or for all accounts, if no account is provided).
 * Used for locally generated feeds.
 */
data class FeedFacetPseudoFiltering(
  override val title: String,
  override val isActive: Boolean,
  val account: AccountID?,
) : FeedFacetSingle
