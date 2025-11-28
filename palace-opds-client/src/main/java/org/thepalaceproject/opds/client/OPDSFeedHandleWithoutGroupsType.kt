package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.Feed
import java.util.concurrent.CompletableFuture

interface OPDSFeedHandleWithoutGroupsType : OPDSFeedHandleType {

  data class Page(
    val pageIndex: Int,
    val pagePrevious: Int?,
    val pageNext: Int?,
    val data: Feed.FeedWithoutGroups
  )

  fun pages(): Int

  fun page(index: Int): CompletableFuture<Page>

  fun feed(): Feed.FeedWithoutGroups

  fun scrollPositionSave(position: Int)

  fun scrollPositionGet(): Int
}
