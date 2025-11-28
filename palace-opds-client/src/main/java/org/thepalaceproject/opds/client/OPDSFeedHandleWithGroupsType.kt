package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.Feed

interface OPDSFeedHandleWithGroupsType : OPDSFeedHandleType {

  /**
   * The current feed.
   */

  fun feed(): Feed.FeedWithGroups
}
