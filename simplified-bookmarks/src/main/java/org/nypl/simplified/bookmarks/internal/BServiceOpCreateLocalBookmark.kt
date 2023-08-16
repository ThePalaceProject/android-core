package org.nypl.simplified.bookmarks.internal

import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
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
  private val bookmark: Bookmark
) : BServiceOp<Bookmark>(logger) {

  override fun runActual(): Bookmark {
    return this.locallySaveBookmark()
  }

  private fun locallySaveBookmark(): Bookmark {
    return try {
      this.logger.debug(
        "[{}]: locally saving bookmark {}",
        this.profile.id.uuid,
        this.bookmark.bookmarkId.value
      )

      val account = this.profile.account(this.accountID)
      val books = account.bookDatabase
      val entry = books.entry(this.bookmark.book)

      when (bookmark) {
        is Bookmark.ReaderBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java)
              ?: throw this.errorNoFormatHandle()

          when (this.bookmark.kind) {
            BookmarkKind.BookmarkLastReadLocation ->
              handle.setLastReadLocation(this.bookmark)

            BookmarkKind.BookmarkExplicit ->
              handle.setBookmarks(
                BServiceBookmarks.normalizeBookmarks(
                  logger = this.logger,
                  profileId = this.profile.id,
                  handle = handle,
                  bookmark = bookmark
                )
              )
          }

          this.publishSavedEvent(this.bookmark)
        }

        is Bookmark.PDFBookmark -> {
          val handle =
            entry.findFormatHandle(BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF::class.java)
              ?: throw this.errorNoFormatHandle()

          when (this.bookmark.kind) {
            BookmarkKind.BookmarkLastReadLocation ->
              handle.setLastReadLocation(this.bookmark)

            BookmarkKind.BookmarkExplicit -> {
              handle.setBookmarks(
                BServiceBookmarks.normalizeBookmarks(
                  logger = this.logger,
                  profileId = this.profile.id,
                  handle = handle,
                  bookmark = bookmark
                )
              )
            }
          }

          this.publishSavedEvent(this.bookmark)
        }

        is Bookmark.AudiobookBookmark -> {
          val handle =
            entry.findFormatHandle(
              BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook::class.java
            ) ?: throw this.errorNoFormatHandle()

          val updatedBookmark = bookmark.copy(
            location = bookmark.location.copy(
              currentOffset = bookmark.location.startOffset + bookmark.location.currentOffset
            )
          )

          when (this.bookmark.kind) {
            BookmarkKind.BookmarkLastReadLocation ->
              handle.setLastReadLocation(updatedBookmark)

            BookmarkKind.BookmarkExplicit ->
              handle.setBookmarks(
                BServiceBookmarks.normalizeBookmarks(
                  logger = this.logger,
                  profileId = this.profile.id,
                  handle = handle,
                  bookmark = updatedBookmark
                )
              )
          }

          this.publishSavedEvent(updatedBookmark)
        }

        else ->
          throw IllegalStateException("Unsupported bookmark type: $bookmark")
      }
    } catch (e: Exception) {
      this.logger.error("error saving bookmark locally: ", e)
      throw e
    }
  }

  private fun publishSavedEvent(updatedBookmark: Bookmark): Bookmark {
    this.bookmarkEventsOut.onNext(BookmarkEvent.BookmarkSaved(this.accountID, updatedBookmark))
    return updatedBookmark
  }

  private fun errorNoFormatHandle(): IllegalStateException {
    this.logger.debug(
      "[{}]: unable to save bookmark; no format handle",
      this.profile.id.uuid
    )
    return IllegalStateException("No format handle")
  }
}
