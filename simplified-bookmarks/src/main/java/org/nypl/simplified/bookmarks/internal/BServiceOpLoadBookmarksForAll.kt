package org.nypl.simplified.bookmarks.internal

import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that loads bookmarks.
 */

internal class BServiceOpLoadBookmarksForAll(
  logger: Logger,
  private val profile: ProfileReadableType,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    try {
      this.logger.debug("[{}]: loading bookmarks for profile", this.profile.id.uuid)

      for (account in this.profile.accounts().values) {
        for (book in account.bookDatabase.books()) {
          try {
            BServiceOpLoadBookmarksForBook(
              logger = this.logger,
              profile = this.profile,
              accountID = account.id,
              book = book,
              bookmarksSource = this.bookmarksSource
            ).call()
          } catch (e: Exception) {
            this.logger.error(
              "[{}]: error loading bookmarks for account {}: ",
              this.profile.id.uuid,
              account.id,
              e
            )
          }
        }
      }
    } catch (e: Exception) {
      this.logger.error("[{}]: error loading bookmarks: ", this.profile.id.uuid, e)
    }
  }
}
