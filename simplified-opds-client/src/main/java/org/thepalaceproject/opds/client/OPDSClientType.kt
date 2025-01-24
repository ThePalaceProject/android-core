package org.thepalaceproject.opds.client

import com.io7m.jattribute.core.AttributeReadableType
import org.nypl.simplified.feeds.api.FeedEntry
import java.util.concurrent.CompletableFuture

/**
 * The interface exposed by OPDS clients.
 */

interface OPDSClientType : AutoCloseable {

  /**
   * The current client state.
   */

  val state: AttributeReadableType<OPDSState>

  /**
   * The ungrouped entries in the current feed state, or an empty list if the current state is not an ungrouped feed.
   */

  val entriesUngrouped: AttributeReadableType<List<FeedEntry>>

  /**
   * The selected in the current feed state, or an empty list if the current state is not an entry.
   */

  val entry: AttributeReadableType<FeedEntry>

  /**
   * `true` if the current history is non-empty.
   */

  val hasHistory: Boolean

  /**
   * Go "backwards" in a feed or entry. This pops the entry on the current history stack and displays it.
   */

  fun goBack(): CompletableFuture<Unit>

  /**
   * This attempts to load the feed at the given URI.
   */

  fun goTo(request: OPDSClientRequest): CompletableFuture<Unit>

  /**
   * Load more of the current feed. This is a no-op if the current state is not an ungrouped feed, or if the
   * current ungrouped feed does not have any more entries.
   */

  fun loadMore(): CompletableFuture<Unit>
}
