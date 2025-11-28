package org.nypl.simplified.bookmarks.api

/**
 * The result of attempting to enable/disable syncing (assuming that the attempt didn't
 * outright fail with an exception).
 */

enum class BookmarkSyncEnableResult {
  SYNC_ENABLE_NOT_SUPPORTED,
  SYNC_ENABLED,
  SYNC_DISABLED
}
