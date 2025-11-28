package org.thepalaceproject.opds.client

import java.util.concurrent.CompletableFuture

interface OPDSFeedHandleType : OPDSHandleType {

  /**
   * Refresh the current feed.
   */

  fun refresh(): CompletableFuture<Unit>
}
