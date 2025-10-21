package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.io7m.jattribute.core.AttributeType
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks. The operation will first create a local
 * bookmark, and will then create a remote bookmark if the local bookmark succeeds. The remote
 * bookmark is only created if the account in question actually allows it.
 */

internal class BServiceOpCreateBookmark(
  logger: Logger,
  private val objectMapper: ObjectMapper,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val httpCalls: BookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: SerializedBookmark,
  private val ignoreRemoteFailures: Boolean,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<SerializedBookmark>(logger) {

  override fun runActual(): SerializedBookmark {
    return try {
      this.createLocalBookmarkFrom(this.bookmark)

      val remoteBookmark =
        BServiceOpCreateRemoteBookmark(
          this.logger,
          this.objectMapper,
          this.httpCalls,
          this.profile,
          this.accountID,
          this.bookmark,
          this.bookmarksSource
        ).runActual()

      this.createLocalBookmarkFrom(remoteBookmark)
    } catch (e: Exception) {
      return if (this.ignoreRemoteFailures) {
        this.createLocalBookmarkFrom(this.bookmark)
      } else {
        throw e
      }
    }
  }

  private fun createLocalBookmarkFrom(
    bookmark: SerializedBookmark
  ): SerializedBookmark {
    return BServiceOpCreateLocalBookmark(
      this.logger,
      this.bookmarkEventsOut,
      this.profile,
      this.accountID,
      bookmark,
      this.bookmarksSource
    ).runActual()
  }
}
