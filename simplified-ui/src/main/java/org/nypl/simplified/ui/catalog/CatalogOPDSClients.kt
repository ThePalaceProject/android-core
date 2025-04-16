package org.nypl.simplified.ui.catalog

import android.content.res.Resources
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountEventDeletion
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedFacetPseudoTitleProviderType
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.main.MainApplication
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.CLEAR_HISTORY
import org.thepalaceproject.opds.client.OPDSClientType
import java.net.URI

class CatalogOPDSClients(
  val profiles: ProfilesControllerType,
  val mainClient: OPDSClientType,
  val booksClient: OPDSClientType,
  val holdsClient: OPDSClientType
) : AutoCloseable {

  private val logger =
    LoggerFactory.getLogger(CatalogOPDSClients::class.java)

  private val resources =
    CloseableCollection.create()

  init {
    this.resources.add(this.mainClient)
    this.resources.add(this.booksClient)
    this.resources.add(this.holdsClient)

    /*
     * When an account is deleted, we need to clear the histories of the OPDS clients so that
     * the catalog can never observe a deleted account.
     */

    val subscription =
      this.profiles.accountEvents()
        .filter { e -> e is AccountEventDeletion.AccountEventDeletionSucceeded }
        .subscribe {
          this.onAccountDeleted()
        }

    this.resources.add(AutoCloseable { subscription.dispose() })
  }

  private fun onAccountDeleted() {
    this.logger.debug("An account has been deleted. Clearing history.")

    UIThread.runOnUIThread {
      this.clearHistory(this.mainClient)
      this.clearHistory(this.booksClient)
      this.clearHistory(this.holdsClient)
    }
  }

  private fun clearHistory(
    client: OPDSClientType
  ) {
    try {
      client.clearHistory()
    } catch (e: Throwable) {
      this.logger.debug("Failed to clear history: ", e)
    }
  }

  fun clientFor(
    part: CatalogPart
  ): OPDSClientType {
    return when (part) {
      CatalogPart.CATALOG -> this.mainClient
      CatalogPart.BOOKS -> this.booksClient
      CatalogPart.HOLDS -> this.holdsClient
    }
  }

  override fun close() {
    this.resources.close()
  }

  fun goToRootFeedFor(
    catalogPart: CatalogPart,
    account: AccountType
  ) {
    val client = this.clientFor(catalogPart)

    when (catalogPart) {
      CatalogPart.CATALOG -> {
        client.goTo(
          OPDSClientRequest.NewFeed(
            accountID = account.id,
            uri = account.catalogURIForAge(18),
            credentials = account.loginState.credentials,
            historyBehavior = CLEAR_HISTORY,
            method = "GET"
          )
        )
      }

      CatalogPart.BOOKS -> {
        client.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = account.id,
            historyBehavior = CLEAR_HISTORY,
            generator = {
              this.profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = "",
                  facetTitleProvider = facetTitleProvider,
                  feedSelection = FeedBooksSelection.BOOKS_FEED_LOANED,
                  filterByAccountID = account.id
                )
              ).get()
            }
          )
        )
      }

      CatalogPart.HOLDS -> {
        client.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = account.id,
            historyBehavior = CLEAR_HISTORY,
            generator = {
              this.profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = "",
                  facetTitleProvider = facetTitleProvider,
                  feedSelection = FeedBooksSelection.BOOKS_FEED_HOLDS,
                  filterByAccountID = account.id
                )
              ).get()
            }
          )
        )
      }
    }
  }

  companion object {
    val facetTitleProvider: FeedFacetPseudoTitleProviderType =
      CatalogFacetPseudoTitleProvider(MainApplication.application.resources)
  }

  private class CatalogFacetPseudoTitleProvider(
    val resources: Resources
  ) : FeedFacetPseudoTitleProviderType {
    override val sortByTitle: String
      get() = this.resources.getString(R.string.feedByTitle)
    override val sortByAuthor: String
      get() = this.resources.getString(R.string.feedByAuthor)
    override val collection: String
      get() = this.resources.getString(R.string.feedCollection)
    override val collectionAll: String
      get() = this.resources.getString(R.string.feedCollectionAll)
    override val sortBy: String
      get() = this.resources.getString(R.string.feedSortBy)
    override val show: String
      get() = this.resources.getString(R.string.feedShow)
    override val showAll: String
      get() = this.resources.getString(R.string.feedShowAll)
    override val showOnLoan: String
      get() = this.resources.getString(R.string.feedShowOnLoan)
  }
}
