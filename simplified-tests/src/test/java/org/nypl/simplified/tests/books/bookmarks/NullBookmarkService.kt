package org.nypl.simplified.tests.books.bookmarks

import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.Futures
import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkServiceProviderType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableStatus
import org.nypl.simplified.bookmarks.api.Bookmarks

class NullBookmarkService(
  val events: Observable<BookmarkEvent>
) : BookmarkServiceType {

  override fun close() {
  }

  override val bookmarkEvents: Observable<BookmarkEvent>
    get() = this.events

  override fun bookmarkSyncAccount(accountID: AccountID, bookID: BookID): FluentFuture<Bookmark?> {
    return FluentFuture.from(null)
  }

  override fun bookmarkSyncAndLoad(accountID: AccountID, book: BookID): FluentFuture<Bookmarks> {
    return FluentFuture.from(
      Futures.immediateFuture(
        Bookmarks(
          lastReadLocal = null,
          lastReadServer = null,
          bookmarks = listOf()
        )
      )
    )
  }

  override fun bookmarkSyncStatus(accountID: AccountID): BookmarkSyncEnableStatus {
    return BookmarkSyncEnableStatus.Changing(accountID)
  }

  override fun bookmarkSyncEnable(accountID: AccountID, enabled: Boolean): FluentFuture<BookmarkSyncEnableResult> {
    return FluentFuture.from(Futures.immediateFuture(BookmarkSyncEnableResult.SYNC_ENABLE_NOT_SUPPORTED))
  }

  override fun bookmarkCreateLocal(accountID: AccountID, bookmark: Bookmark): FluentFuture<Unit> {
    return FluentFuture.from(Futures.immediateFuture(Unit))
  }

  override fun bookmarkCreateRemote(accountID: AccountID, bookmark: Bookmark): FluentFuture<Unit> {
    return FluentFuture.from(Futures.immediateFuture(Unit))
  }

  override fun bookmarkDelete(accountID: AccountID, bookmark: Bookmark): FluentFuture<Unit> {
    return FluentFuture.from(Futures.immediateFuture(Unit))
  }

  override fun bookmarkLoad(
    accountID: AccountID,
    book: BookID,
    lastReadBookmarkServer: Bookmark?
  ): FluentFuture<Bookmarks> {
    return FluentFuture.from(
      Futures.immediateFuture(
        Bookmarks(
          lastReadLocal = null,
          lastReadServer = null,
          bookmarks = listOf()
        )
      )
    )
  }

  companion object : BookmarkServiceProviderType {
    override fun createService(
      requirements: BookmarkServiceProviderType.Requirements
    ): BookmarkServiceType {
      return NullBookmarkService(requirements.events)
    }
  }
}
