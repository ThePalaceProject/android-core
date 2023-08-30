package org.nypl.simplified.feeds.api

import com.google.common.util.concurrent.FluentFuture
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI

/**
 * The type of feed loaders.
 */

interface FeedLoaderType {

  /**
   * `true` if feeds should contain only books that the application can open
   */

  var showOnlySupportedBooks: Boolean

  /**
   * Load a feed from the given URI.
   *
   * @param uri           The URI
   * @param credentials   HTTP credentials, if any
   *
   * @return A future that can be used to cancel the loading feed
   */

  fun fetchURI(
    accountID: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): FluentFuture<FeedLoaderResult>
}
