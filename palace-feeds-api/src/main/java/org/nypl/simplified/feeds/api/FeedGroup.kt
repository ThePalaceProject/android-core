package org.nypl.simplified.feeds.api

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSGroup
import java.net.URI

/**
 * A feed group.
 *
 * @property account The account from which the feed group was taken
 * @property groupTitle The group title
 * @property groupURI The URI of the group
 * @property groupEntries The list of entries in the group
 */
data class FeedGroup(
  /**
   * @return The account from which the feed group was taken
   */
  val account: AccountID,
  /**
   * @return The group title
   */
  val groupTitle: String,
  /**
   * @return The URI of the group
   */
  val groupURI: URI,
  /**
   * @return The list of entries in the group
   */
  val groupEntries: List<FeedEntry>
) {
  companion object {
    /**
     * @param b An OPDS group
     *
     * @return A group from the given OPDS group
     */
    fun fromOPDSGroup(
      accountID: AccountID,
      filter: (OPDSAcquisitionFeedEntry) -> Boolean,
      b: OPDSGroup
    ): FeedGroup {
      val entries =
        b.entries
          .filter(filter)
          .map { FeedEntryOPDS(accountID, it) }

      return FeedGroup(
        account = accountID,
        groupTitle = b.title,
        groupURI = b.uri,
        groupEntries = entries
      )
    }

    /**
     * @param bs A map of OPDS groups
     *
     * @return A map of groups from the given OPDS groups
     */
    fun fromOPDSGroups(
      accountID: AccountID,
      filter: (OPDSAcquisitionFeedEntry) -> Boolean,
      bs: Map<String, OPDSGroup>
    ): Map<String, FeedGroup> =
      bs.mapValues { (_, group) ->
        fromOPDSGroup(
          accountID = accountID,
          filter = filter,
          b = group
        )
      }
  }
}
