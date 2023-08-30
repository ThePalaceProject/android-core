package org.nypl.simplified.tests.mocking

import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.Futures
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import java.io.IOException
import java.net.URI

class MockCrashingFeedLoader : FeedLoaderType {

  override var showOnlySupportedBooks: Boolean =
    false

  override fun fetchURI(
    accountID: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): FluentFuture<FeedLoaderResult> {
    return FluentFuture.from(Futures.immediateFailedFuture(IOException("Ouch!")))
  }
}
