package org.thepalaceproject.opds.client.internal

import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.thepalaceproject.opds.client.OPDSState

internal interface OPDSCmdContextType {

  fun setState(
    newState: OPDSState
  )

  fun setStateReplaceTop(
    newState: OPDSState
  )

  fun shutDown()

  fun entriesUngrouped(): List<FeedEntry>

  fun setEntriesUngrouped(
    entries: List<FeedEntry>
  )

  fun operationCancelled()

  val feedLoader: FeedLoaderType

  val state: OPDSState
}
