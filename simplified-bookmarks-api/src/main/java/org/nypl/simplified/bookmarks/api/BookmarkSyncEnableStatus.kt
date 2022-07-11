package org.nypl.simplified.bookmarks.api

import org.nypl.simplified.accounts.api.AccountID

/**
 * The current status of the bookmark syncing configuration switch. The configuration is either
 * idle, or is in the process of changing.
 */

sealed class BookmarkSyncEnableStatus {

  /**
   * The switch is idle.
   */

  data class Idle(
    val accountID: AccountID,
    val status: BookmarkSyncEnableResult
  ) : BookmarkSyncEnableStatus()

  /**
   * The switch is changing.
   */

  data class Changing(
    val accountID: AccountID
  ) : BookmarkSyncEnableStatus()
}
