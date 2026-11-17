package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import java.net.URI

/**
 * A functional interface for fetching feed content from a URI.
 */

fun interface FeedFetchType {
  fun fetch(
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    version: FeedFetchVersion,
  ): FeedFetchResult
}
