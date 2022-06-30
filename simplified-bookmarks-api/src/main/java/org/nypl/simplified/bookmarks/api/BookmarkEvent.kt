package org.nypl.simplified.bookmarks.api

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.bookmark.Bookmark

/**
 * The type of events published by the bookmark controller.
 */

sealed class BookmarkEvent {

  /**
   * The status of bookmark syncing changed.
   */

  data class BookmarkSyncSettingChanged(
    val accountID: AccountID,
    val status: BookmarkSyncEnableStatus
  ) : BookmarkEvent()

  /**
   * Synchronizing bookmarks for the given account has started.
   */

  data class BookmarkSyncStarted(
    val accountID: AccountID
  ) : BookmarkEvent()

  /**
   * Synchronizing bookmarks for the given account has finished.
   */

  data class BookmarkSyncFinished(
    val accountID: AccountID
  ) : BookmarkEvent()

  /**
   * A bookmark was saved for the given account.
   */

  data class BookmarkSaved(
    val accountID: AccountID,
    val bookmark: Bookmark
  ) : BookmarkEvent()
}
