package org.nypl.simplified.feeds

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import java.io.InputStream
import java.net.URI

/**
 * The result of fetching feed content.
 */
data class FeedFetchResult(
  val body: InputStream,
  val accessToken: String?,
  val contentType: MIMEType?,
)

/**
 * A functional interface for fetching feed content from a URI.
 */
fun interface FeedFetchType {
  fun fetch(
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    acceptHeader: String?,
  ): FeedFetchResult
}
