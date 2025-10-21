package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.opds.core.OPDSFacet
import java.io.Serializable

sealed interface FeedFacet : Serializable {

  val title: String

  val isActive: Boolean

  /**
   * The type of facets that are "single". That is, they are fully handled by making a single
   * request.
   */

  sealed interface FeedFacetSingle : FeedFacet

  /**
   * The type of facets taken from OPDS 1.2 feeds.
   */

  sealed class FeedFacetOPDS12 : FeedFacet {
    abstract val accountID: AccountID
  }

  /**
   * A facet taken from an actual OPDS 1.2 feed.
   */

  data class FeedFacetOPDS12Single(
    override val accountID: AccountID,
    val opdsFacet: OPDSFacet
  ) : FeedFacetOPDS12(), FeedFacetSingle {
    override val title: String =
      this.opdsFacet.title
    override val isActive: Boolean =
      this.opdsFacet.isActive

    companion object {
      const val ENTRYPOINT_FACET_GROUP_TYPE = "http://librarysimplified.org/terms/rel/entrypoint"
    }
  }

  /**
   * A composite facet made up of a combination of facets from a single OPDS 1.2 feed. This type
   * of facet requires a client to make multiple requests in order to effectively end up at a
   * single facet.
   */

  data class FeedFacetOPDS12Composite(
    val facets: List<FeedFacetOPDS12Single>,
    override val title: String,
    override val isActive: Boolean
  ) : FeedFacetOPDS12() {
    init {
      check(facets.isNotEmpty()) {
        "Composite facets must contain at least one facet."
      }
      check(facets.map { f -> f.accountID }.toSet().size == 1) {
        "The values that make up a composite facet must have the same account ID."
      }
    }

    override val accountID: AccountID
      get() = this.facets[0].accountID
  }

  /**
   * The type of pseudo-facets.
   *
   * This is used to provide facets for locally generated feeds.
   */

  sealed class FeedFacetPseudo : FeedFacetSingle {

    /**
     * A filtering facet for a specific account (or for all accounts, if an account isn't provided).
     */

    data class FilteringForAccount(
      override val title: String,
      override val isActive: Boolean,
      val account: AccountID?
    ) : FeedFacetPseudo()

    /**
     * A sorting facet.
     */

    data class Sorting(
      override val title: String,
      override val isActive: Boolean,
      val sortBy: SortBy
    ) : FeedFacetPseudo() {

      enum class SortBy {

        /**
         * Sort the feed in question by author.
         */

        SORT_BY_AUTHOR,

        /**
         * Sort the feed in question by book title.
         */

        SORT_BY_TITLE
      }
    }
  }
}
