package org.nypl.simplified.ui.catalog

import android.content.res.Resources
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedFacetPseudoTitleProviderType
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.main.MainApplication
import org.thepalaceproject.opds.client.OPDSClientRequest
import org.thepalaceproject.opds.client.OPDSClientType
import java.net.URI

data class CatalogOPDSClients(
  val mainClient: OPDSClientType,
  val booksClient: OPDSClientType,
  val holdsClient: OPDSClientType
) : AutoCloseable {

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
    this.mainClient.close()
    this.booksClient.close()
    this.holdsClient.close()
  }

  fun goToRootFeedFor(
    profiles: ProfilesControllerType,
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
            method = "GET"
          )
        )
      }

      CatalogPart.BOOKS -> {
        client.goTo(
          OPDSClientRequest.GeneratedFeed(
            accountID = account.id,
            generator = {
              profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = "",
                  facetTitleProvider = facetTitles,
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
            generator = {
              profiles.profileFeed(
                ProfileFeedRequest(
                  uri = URI.create("Books"),
                  title = "",
                  facetTitleProvider = facetTitles,
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

  private val facetTitles =
    CatalogFacetPseudoTitleProvider(MainApplication.application.resources)

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
