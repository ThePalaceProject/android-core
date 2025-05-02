package org.nypl.simplified.ui.catalog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.future.await
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSFeedHandleWithoutGroupsType

class CatalogFeedPagingSource(
  val feedHandle: OPDSFeedHandleWithoutGroupsType
) : PagingSource<Int, FeedEntry>() {

  private val logger =
    LoggerFactory.getLogger(CatalogFeedPagingSource::class.java)

  override fun getRefreshKey(
    state: PagingState<Int, FeedEntry>
  ): Int? {
    val anchor = state.anchorPosition
    if (anchor != null) {
      val closest = state.closestPageToPosition(anchor)
      val prevKey = closest?.prevKey
      if (prevKey != null) {
        return prevKey + 1
      }
    }
    return null
  }

  override suspend fun load(
    params: LoadParams<Int>
  ): LoadResult<Int, FeedEntry> {
    val page = params.key ?: 0
    this.logger.debug("Load: Page {}", page)
    return try {
      val pageData = this.feedHandle.page(page).await()
      LoadResult.Page(
        data = pageData.data.entriesInOrder,
        prevKey = pageData.pagePrevious,
        nextKey = pageData.pageNext
      )
    } catch (e: Throwable) {
      if (e !is kotlinx.coroutines.CancellationException) {
        this.logger.debug("Failed to load feed: ", e)
      }
      LoadResult.Error(e)
    }
  }
}
