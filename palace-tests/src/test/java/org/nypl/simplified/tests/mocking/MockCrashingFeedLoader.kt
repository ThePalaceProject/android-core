package org.nypl.simplified.tests.mocking

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import java.io.IOException
import java.net.URI
import java.util.concurrent.CompletableFuture

class MockCrashingFeedLoader : FeedLoaderType {

  override var showOnlySupportedBooks: Boolean =
    false

  override fun fetchURI(
    accountID: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): CompletableFuture<FeedLoaderResult> {
    val future = CompletableFuture<FeedLoaderResult>()
    future.completeExceptionally(IOException("Ouch!"))
    return future
  }
}
