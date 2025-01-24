package org.thepalaceproject.opds.client.internal

import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.thepalaceproject.opds.client.OPDSState

internal interface OPDSCmdContextType {

  fun setEntriesUngrouped(
    entries: List<FeedEntry>
  )

  fun setEntriesGrouped(
    groups: List<FeedGroup>
  )

  fun setState(
    newState: OPDSState
  )

  fun setStateSavingHistory(
    newState: OPDSState.OPDSStateHistoryParticipant
  )

  fun shutDown()

  fun entriesUngrouped(): List<FeedEntry>

  val feedLoader: FeedLoaderType

  val state: OPDSState
}
