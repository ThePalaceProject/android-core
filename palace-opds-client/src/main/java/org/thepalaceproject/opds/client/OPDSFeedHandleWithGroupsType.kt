package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedDataWithGroups

interface OPDSFeedHandleWithGroupsType : OPDSFeedHandleType {
  /**
   * The current feed.
   */

  fun feed(): FeedDataWithGroups
}
