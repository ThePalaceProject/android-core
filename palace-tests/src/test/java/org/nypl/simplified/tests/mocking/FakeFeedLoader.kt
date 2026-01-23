package org.nypl.simplified.tests.mocking

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import java.net.URI
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CompletableFuture

class FakeFeedLoader : FeedLoaderType {

  val fetchURIQueue: Queue<CompletableFuture<FeedLoaderResult>> =
    LinkedList()

  data class URIRequest(
    val accountID: AccountID,
    val uri: URI,
    val credentials: AccountAuthenticationCredentials?,
    val method: String
  )

  val requests: Queue<URIRequest> =
    LinkedList()

  fun addResponse(
    result: FeedLoaderResult
  ): CompletableFuture<FeedLoaderResult> {
    val future = CompletableFuture<FeedLoaderResult>()
    future.complete(result)
    this.fetchURIQueue.add(future)
    return future
  }

  override var showOnlySupportedBooks: Boolean =
    true

  override fun fetchURI(
    accountID: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): CompletableFuture<FeedLoaderResult> {
    this.requests.add(URIRequest(accountID, uri, credentials, method))
    return fetchURIQueue.poll()!!
  }
}
