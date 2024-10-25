package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks.
 */

internal class BServiceOpDeleteBookmark(
  logger: Logger,
  private val httpCalls: BookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: SerializedBookmark,
  private val ignoreRemoteFailures: Boolean
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    try {
      this.remotelyDeleteBookmark()
    } catch (e: Exception) {
      this.logger.debug(
        "[{}]: failed to delete bookmark {}: ",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value,
        e
      )
      if (!this.ignoreRemoteFailures) {
        throw e
      }
    }

    this.locallyDeleteBookmark()
  }

  private fun remotelyDeleteBookmark() {
    return try {
      this.logger.debug(
        "[{}]: remote deleting bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      if (this.bookmark.kind == BookmarkKind.BookmarkExplicit) {
        var bookmarkURI = this.bookmark.uri
        if (bookmarkURI == null) {
          val bookmarkEquivalent = findEquivalentBookmark()
          if (bookmarkEquivalent == null) {
            this.logger.debug(
              "[{}]: cannot remotely delete bookmark {} because it has no URI",
              this.profile.id.uuid,
              this.bookmark.bookmarkId.value
            )
            throw IllegalStateException("Bookmark has no URI.")
          }
          bookmarkURI = bookmarkEquivalent.uri!!
        }

        val account = this.profile.account(this.accountID)
        val syncInfo = BSyncableAccount.ofAccount(account)
        if (syncInfo == null) {
          this.logger.debug(
            "[{}]: cannot remotely delete bookmark {} because the account is not syncable",
            this.profile.id.uuid,
            this.bookmark.bookmarkId.value
          )
          throw IllegalStateException("Account is not syncable.")
        }

        this.httpCalls.bookmarkDelete(
          account = account,
          bookmarkURI = bookmarkURI,
          credentials = syncInfo.credentials
        )
        Unit
      } else {
        Unit
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error deleting bookmark: ", this.profile.id.uuid, e)
      throw e
    }
  }

  /**
   * If we've handed the bookmark service a bookmark that doesn't have a URI in it, then
   * we won't be able to delete it from the server. We might, however, have a bookmark
   * that's equivalent to this bookmark that _does_ have a server URI in it. If we do, then
   * use _that_ bookmark to perform the server-side deletion. A bookmarking system with a
   * sane (read: non-W3C) design would have given bookmarks unique identifiers.
   */

  private fun findEquivalentBookmark(): SerializedBookmark? {
    val account = this.profile.account(this.accountID)
    val books = account.bookDatabase
    val entry = books.entry(this.bookmark.book)

    for (handle in entry.formatHandles) {
      for (possiblyEquivalentBookmark in handle.format.bookmarks) {
        if (this.bookmarksAreInterchangeable(this.bookmark, possiblyEquivalentBookmark)) {
          return possiblyEquivalentBookmark
        }
      }
    }
    return null
  }

  private fun bookmarksAreInterchangeable(
    bookmarkA: SerializedBookmark,
    bookmarkB: SerializedBookmark
  ): Boolean {
    return (bookmarkA.kind == bookmarkB.kind) &&
      (bookmarkA.book == bookmarkB.book) &&
      (bookmarkA.location == bookmarkB.location)
  }

  private fun locallyDeleteBookmark() {
    try {
      this.logger.debug(
        "[{}]: locally deleting bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val account = this.profile.account(this.accountID)
      val books = account.bookDatabase
      val entry = books.entry(this.bookmark.book)

      for (handle in entry.formatHandles) {
        when (this.bookmark.kind) {
          BookmarkKind.BookmarkLastReadLocation ->
            handle.setLastReadLocation(null)

          BookmarkKind.BookmarkExplicit ->
            handle.deleteBookmark(this.bookmark.bookmarkId)
        }
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: Error deleting bookmark locally: ", this.profile.id.uuid, e)
      throw e
    }
  }
}
