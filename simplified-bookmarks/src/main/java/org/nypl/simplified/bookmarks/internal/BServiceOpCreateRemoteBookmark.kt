package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkAnnotations
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.books.api.bookmark.Bookmark
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
  private val bookmark: Bookmark
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    this.remotelySendBookmark()
  }

  private fun remotelySendBookmark() {
    try {
      this.logger.debug(
        "[{}]: remote sending bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val syncInfo = BSyncableAccount.ofAccount(this.profile.account(this.accountID))
      if (syncInfo == null) {
        this.logger.debug(
          "[{}]: cannot remotely send bookmark {} because the account is not syncable",
          this.profile.id.uuid,
          this.bookmark.bookmarkId.value
        )
        return
      }

      val bookmarkAnnotation = when (this.bookmark) {
        is Bookmark.ReaderBookmark ->
          BookmarkAnnotations.fromReaderBookmark(this.objectMapper, this.bookmark)
        is Bookmark.AudiobookBookmark ->
          BookmarkAnnotations.fromAudiobookBookmark(this.objectMapper, this.bookmark)
        else ->
          throw IllegalStateException("Unsupported bookmark type: $bookmark")
      }

      this.httpCalls.bookmarkAdd(
        annotationsURI = syncInfo.annotationsURI,
        credentials = syncInfo.credentials,
        bookmark = bookmarkAnnotation
      )
    } catch (e: Exception) {
      this.logger.error("error sending bookmark: ", e)
    }
  }
}
