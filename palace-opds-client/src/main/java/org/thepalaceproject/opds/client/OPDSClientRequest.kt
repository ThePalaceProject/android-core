package org.thepalaceproject.opds.client

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import java.net.URI
import java.util.UUID

sealed class OPDSClientRequest {

  abstract val requestID: UUID

  abstract val accountID: AccountID

  abstract val uri: URI

  abstract val historyBehavior: HistoryBehavior

  abstract fun withHistoryBehaviour(
    historyBehavior: HistoryBehavior
  ): OPDSClientRequest

  enum class HistoryBehavior {
    /**
     * The new item will added to the history, becoming the new tip.
     */

    ADD_TO_HISTORY,

    /**
     * The new item will replace the current tip of the history, yielding a history of
     * equal size.
     */

    REPLACE_TIP,

    /**
     * The history will be cleared and the new item added to the empty history.
     */

    CLEAR_HISTORY
  }

  data class NewFeed(
    override val accountID: AccountID,
    override val uri: URI,
    val credentials: AccountAuthenticationCredentials?,
    val method: String,
    override val historyBehavior: HistoryBehavior
  ) : OPDSClientRequest() {
    override val requestID: UUID =
      UUID.randomUUID()

    override fun withHistoryBehaviour(
      historyBehavior: HistoryBehavior
    ): NewFeed {
      return this.copy(historyBehavior = historyBehavior)
    }
  }

  data class GeneratedFeed(
    override val accountID: AccountID,
    override val historyBehavior: HistoryBehavior,
    val generator: () -> Feed
  ) : OPDSClientRequest() {
    override val uri =
      URI.create("urn:generated:account:${this.accountID}")
    override val requestID: UUID =
      UUID.randomUUID()

    override fun withHistoryBehaviour(
      historyBehavior: HistoryBehavior
    ): GeneratedFeed {
      return this.copy(historyBehavior = historyBehavior)
    }
  }

  data class ExistingEntry(
    override val historyBehavior: HistoryBehavior,
    val entry: FeedEntry
  ) : OPDSClientRequest() {
    override val accountID: AccountID =
      this.entry.accountID
    override val uri: URI =
      URI.create("urn:entry:${this.accountID}:${this.entry.bookID}")
    override val requestID: UUID =
      UUID.randomUUID()

    override fun withHistoryBehaviour(
      historyBehavior: HistoryBehavior
    ): ExistingEntry {
      return this.copy(historyBehavior = historyBehavior)
    }
  }

  data class ResolvedCompositeOPDS12Facet(
    override val historyBehavior: HistoryBehavior,
    val credentials: AccountAuthenticationCredentials?,
    val method: String,
    val facet: FeedFacet.FeedFacetOPDS12Composite,
  ) : OPDSClientRequest() {
    override val uri =
      this.facet.facets[0].opdsFacet.uri
    override val requestID: UUID =
      UUID.randomUUID()
    override val accountID: AccountID =
      this.facet.accountID
    override fun withHistoryBehaviour(
      historyBehavior: HistoryBehavior
    ): ResolvedCompositeOPDS12Facet {
      return this.copy(historyBehavior = historyBehavior)
    }
  }
}
