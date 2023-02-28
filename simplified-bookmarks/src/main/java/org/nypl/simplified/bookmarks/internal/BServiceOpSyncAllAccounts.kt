package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
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
  private val profile: ProfileReadableType
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
          bookID = null
        ).runActual()
      } catch (e: Exception) {
        this.logger.debug("failed to sync account {}: ", account.uuid, e)
      }
    }
  }
}
