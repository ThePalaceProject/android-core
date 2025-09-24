package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks.
 */

internal class BServiceOpCreateRemoteBookmark(
  logger: Logger,
  private val objectMapper: ObjectMapper,
  private val httpCalls: BookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: SerializedBookmark,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<SerializedBookmark>(logger) {

  override fun runActual(): SerializedBookmark {
    return this.remotelySendBookmark()
  }

  private fun remotelySendBookmark(): SerializedBookmark {
    return try {
      this.logger.debug(
        "[{}]: remote sending bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      this.bookmarksSource.set(
        BookmarkAttributes.addBookmark(
          this.bookmarksSource.get(),
          this.accountID,
          this.bookmark
        )
      )

      val account = this.profile.account(this.accountID)
      val syncInfo = BSyncableAccount.ofAccount(account)
      if (syncInfo == null) {
        this.logger.debug(
          "[{}]: cannot remotely send bookmark {} because the account is not syncable",
          this.profile.id.uuid,
          this.bookmark.bookmarkId.value
        )
        return this.bookmark
      }

      val bookmarkAnnotation =
        BookmarkAnnotations.fromSerializedBookmark(this.objectMapper, this.bookmark)

      val bookmarkUri =
        this.httpCalls.bookmarkAdd(
          account = account,
          annotationsURI = syncInfo.annotationsURI,
          credentials = syncInfo.credentials,
          bookmark = bookmarkAnnotation
        ) ?: throw IllegalStateException("Server HTTP call failed")

      return this.bookmark.withURI(bookmarkUri)
    } catch (e: Exception) {
      this.logger.debug("error sending bookmark: ", e)
      throw e
    }
  }
}
