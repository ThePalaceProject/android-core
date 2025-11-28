package org.nypl.simplified.bookmarks.internal

import com.io7m.jattribute.core.AttributeType
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that handles user-created bookmarks.
 */

internal class BServiceOpCreateLocalBookmark(
  logger: Logger,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val profile: ProfileReadableType,
  private val accountID: AccountID,
  private val bookmark: SerializedBookmark,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<SerializedBookmark>(logger) {

  override fun runActual(): SerializedBookmark {
    return this.locallySaveBookmark()
  }

  private fun locallySaveBookmark(): SerializedBookmark {
    return try {
      this.logger.debug(
        "[{}]: locally saving bookmark {}",
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

      val account =
        this.profile.account(this.accountID)
      val books =
        account.bookDatabase
      val entry =
        books.entry(this.bookmark.book)

      for (handle in entry.formatHandles) {
        when (this.bookmark.kind) {
          BookmarkKind.BookmarkExplicit -> {
            handle.addBookmark(this.bookmark)
          }
          BookmarkKind.BookmarkLastReadLocation -> {
            handle.setLastReadLocation(this.bookmark)
          }
        }
        this.publishSavedEvent(this.bookmark)
      }

      this.bookmark
    } catch (e: Exception) {
      this.logger.debug("error saving bookmark locally: ", e)
      throw e
    }
  }

  private fun publishSavedEvent(updatedBookmark: SerializedBookmark): SerializedBookmark {
    this.bookmarkEventsOut.onNext(BookmarkEvent.BookmarkSaved(this.accountID, updatedBookmark))
    return updatedBookmark
  }
}
