package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.presentableerror.api.PresentableErrorType

sealed class OPDSState {

  sealed class OPDSStateHistoryParticipant : OPDSState()

  /**
   * The initial state, before the client has been asked to do anything.
   */

  data object Initial : OPDSState()

  /**
   * A feed or entry is currently loading.
   */

  data class Loading(
    val request: OPDSClientRequest
  ) : OPDSState()

  /**
   * A feed or entry failed to load.
   */

  data class Error(
    val message: PresentableErrorType,
    val request: OPDSClientRequest
  ) : OPDSState()

  data class LoadedFeedWithGroups(
    val request: OPDSClientRequest.NewFeed,
    val feed: Feed.FeedWithGroups
  ) : OPDSStateHistoryParticipant()

  data class LoadedFeedWithoutGroups(
    val request: OPDSClientRequest.NewFeed,
    val feed: Feed.FeedWithoutGroups
  ) : OPDSStateHistoryParticipant()

  data class LoadedFeedEntry(
    val request: OPDSClientRequest.ExistingEntry
  ) : OPDSStateHistoryParticipant()
}
