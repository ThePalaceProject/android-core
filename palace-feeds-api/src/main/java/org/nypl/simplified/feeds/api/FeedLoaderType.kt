package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * The type of feed loaders.
 */

interface FeedLoaderType {
  /**
   * `true` if feeds should contain only books that the application can open
   */

  var showOnlySupportedBooks: Boolean

  /**
   * `true` if the client should request OPDS 2.0 serializations from the server.
   *
   * When `true`, the loader sends an `Accept: application/opds+json` header
   * and parses the response as OPDS 2.0. When `false` (the default), the
   * loader does not send the header and the server responds with OPDS 1.2.
   */

  var wantsOpds20: Boolean

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
  ): CompletableFuture<FeedLoaderResult>
}
