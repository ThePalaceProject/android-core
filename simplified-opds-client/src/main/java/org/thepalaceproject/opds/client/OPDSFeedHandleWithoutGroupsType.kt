package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.Feed
import java.util.concurrent.CompletableFuture

interface OPDSFeedHandleWithoutGroupsType : OPDSFeedHandleType {

  /**
   * The current feed.
   */

  fun feed(): Feed.FeedWithoutGroups

  /**
   * Load more of the current feed. This is a no-op if the current state is not an ungrouped feed, or if the
   * current ungrouped feed does not have any more entries.
   */

  fun loadMore(): CompletableFuture<Unit>

}
