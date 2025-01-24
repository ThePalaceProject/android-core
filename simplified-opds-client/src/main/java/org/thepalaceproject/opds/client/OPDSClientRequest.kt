package org.thepalaceproject.opds.client

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntry
import java.net.URI

sealed class OPDSClientRequest {

  data class NewFeed(
    val accountID: AccountID,
    val uri: URI,
    val credentials: AccountAuthenticationCredentials?,
    val method: String
  ) : OPDSClientRequest()

  data class ExistingEntry(
    val entry: FeedEntry
  ) : OPDSClientRequest()
}
