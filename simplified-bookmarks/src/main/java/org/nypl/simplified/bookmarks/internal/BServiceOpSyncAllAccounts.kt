package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.io7m.jattribute.core.AttributeType
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that synchronizes bookmarks for all accounts that want it.
 */

internal class BServiceOpSyncAllAccounts(
  logger: Logger,
  private val httpCalls: BookmarkHTTPCallsType,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val objectMapper: ObjectMapper,
  private val profile: ProfileReadableType,
  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>>,
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    val accounts = this.profile.accounts().keys
    for (account in accounts) {
      try {
        BServiceOpSyncOneAccount(
          this.logger,
          this.httpCalls,
          this.bookmarkEventsOut,
          this.objectMapper,
          this.profile,
          account,
          this.bookmarksSource
        ).runActual()
      } catch (e: Exception) {
        this.logger.debug("failed to sync account {}: ", account.uuid, e)
      }
    }
  }
}
