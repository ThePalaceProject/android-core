package org.thepalaceproject.opds.client

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import java.net.URI
import java.util.UUID

sealed class OPDSClientRequest {

  abstract val requestID: UUID

  abstract val accountID: AccountID

  abstract val uri: URI

  data class NewFeed(
    override val accountID: AccountID,
    override val uri: URI,
    val credentials: AccountAuthenticationCredentials?,
    val method: String
  ) : OPDSClientRequest() {
    override val requestID: UUID =
      UUID.randomUUID()
  }

  data class GeneratedFeed(
    override val accountID: AccountID,
    val generator: () -> Feed
  ) : OPDSClientRequest() {
    override val uri =
      URI.create("urn:generated:account:${this.accountID}")
    override val requestID: UUID =
      UUID.randomUUID()
  }

  data class ExistingEntry(
    val entry: FeedEntry
  ) : OPDSClientRequest() {
    override val accountID: AccountID =
      this.entry.accountID
    override val uri: URI =
      URI.create("urn:entry:${this.accountID}:${this.entry.bookID}")
    override val requestID: UUID =
      UUID.randomUUID()
  }
}
