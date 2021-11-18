package org.nypl.simplified.books.reader.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.slf4j.Logger

/**
 * An operation that synchronizes bookmarks for all accounts that want it.
 */

internal class RBServiceOpSyncAllAccounts(
  logger: Logger,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val bookmarkEventsOut: Subject<ReaderBookmarkEvent>,
  private val objectMapper: ObjectMapper,
  private val profile: ProfileReadableType
) : RBServiceOp<Unit>(logger) {

  override fun runActual() {
    val accounts = this.profile.accounts().keys
    for (account in accounts) {
      try {
        RBServiceOpSyncOneAccount(
          this.logger,
          this.httpCalls,
          this.bookmarkEventsOut,
          this.objectMapper,
          this.profile,
          account
        ).runActual()
      } catch (e: Exception) {
        this.logger.debug("failed to sync account {}: ", account.uuid, e)
      }
    }
  }
}
