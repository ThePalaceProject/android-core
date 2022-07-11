package org.nypl.simplified.bookmarks.api

import com.google.common.util.concurrent.FluentFuture
import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark

/**
 * The "usable" bookmark service interface. Usable, in this sense, refers to the
 * fact that clients are able to use the service but aren't able to shut it down.
 */

interface BookmarkServiceUsableType {

  /**
   * An observable that publishes events about bookmarks.
   */

  val bookmarkEvents: Observable<BookmarkEvent>

  /**
   * Fetch the bookmark sync status for the given account.
   */

  fun bookmarkSyncStatus(
    accountID: AccountID
  ): BookmarkSyncEnableStatus

  /**
   * Enable/disable bookmark syncing on the server.
   */

  fun bookmarkSyncEnable(
    accountID: AccountID,
    enabled: Boolean
  ): FluentFuture<BookmarkSyncEnableResult>

  /**
   * Sync the bookmarks for the given account.
   */
  fun bookmarkSyncAccount(
    accountID: AccountID
  ): FluentFuture<Unit>

  /**
   * Sync the bookmarks for the given account, and load bookmarks for the given book.
   */
  fun bookmarkSyncAndLoad(
    accountID: AccountID,
    book: BookID
  ): FluentFuture<Bookmarks>

  /**
   * The user wants their current bookmarks.
   */

  fun bookmarkLoad(
    accountID: AccountID,
    book: BookID
  ): FluentFuture<Bookmarks>

  /**
   * The user has created a bookmark.
   */

  fun bookmarkCreate(
    accountID: AccountID,
    bookmark: Bookmark
  ): FluentFuture<Unit>

  /**
   * The user has requested that a bookmark be deleted.
   */

  fun bookmarkDelete(
    accountID: AccountID,
    bookmark: Bookmark
  ): FluentFuture<Unit>
}
