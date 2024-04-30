package org.nypl.simplified.bookmarks.api

import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import java.util.concurrent.CompletableFuture

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
   * Sync the bookmarks for the given account, and load bookmarks for the given book.
   */
  fun bookmarkSyncAndLoad(
    accountID: AccountID,
    book: BookID
  ): CompletableFuture<BookmarksForBook>

  /**
   * Sync the bookmarks for the given account.
   */
  fun bookmarkSyncAccount(
    accountID: AccountID
  ): CompletableFuture<List<SerializedBookmark>>

  /**
   * The user wants their current bookmarks.
   */

  fun bookmarkLoad(
    accountID: AccountID,
    book: BookID
  ): CompletableFuture<BookmarksForBook>

  /**
   * Create a local bookmark.
   */

  fun bookmarkCreateLocal(
    accountID: AccountID,
    bookmark: SerializedBookmark
  ): CompletableFuture<SerializedBookmark>

  /**
   * Create a remote bookmark.
   */

  fun bookmarkCreateRemote(
    accountID: AccountID,
    bookmark: SerializedBookmark
  ): CompletableFuture<SerializedBookmark>

  /**
   * Create a local bookmark, and then create a remote bookmark if necessary.
   */

  fun bookmarkCreate(
    accountID: AccountID,
    bookmark: SerializedBookmark,
    ignoreRemoteFailures: Boolean
  ): CompletableFuture<SerializedBookmark>

  /**
   * The user has requested that a bookmark be deleted.
   */

  fun bookmarkDelete(
    accountID: AccountID,
    bookmark: SerializedBookmark,
    ignoreRemoteFailures: Boolean
  ): CompletableFuture<Unit>
}
