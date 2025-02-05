package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import java.util.UUID

sealed class OPDSState {

  sealed class OPDSStateHistoryParticipant : OPDSState()

  abstract val id: UUID

  /**
   * The initial state, before the client has been asked to do anything.
   */

  data class Initial(
    override val id: UUID
  ) : OPDSState()

  /**
   * A feed or entry is currently loading.
   */

  data class Loading(
    override val id: UUID,
    val request: OPDSClientRequest
  ) : OPDSState()

  /**
   * A feed or entry failed to load.
   */

  data class Error(
    override val id: UUID,
    val message: PresentableErrorType,
    val request: OPDSClientRequest
  ) : OPDSState()

  data class LoadedFeedWithGroups(
    override val id: UUID,
    val request: OPDSClientRequest.NewFeed,
    val feed: Feed.FeedWithGroups
  ) : OPDSStateHistoryParticipant()

  data class LoadedFeedWithoutGroups(
    override val id: UUID,
    val request: OPDSClientRequest.NewFeed,
    val feed: Feed.FeedWithoutGroups
  ) : OPDSStateHistoryParticipant()

  data class LoadedFeedEntry(
    override val id: UUID,
    val request: OPDSClientRequest.ExistingEntry
  ) : OPDSStateHistoryParticipant()
}
