package org.nypl.simplified.bookmarks.internal

import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableStatus
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that enables account syncing by sending a request to the server to enable
 * the preference in the patron's user profile.
 */

internal class BServiceOpEnableSync(
  logger: Logger,
  private val accountsSyncChanging: MutableSet<AccountID>,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val httpCalls: BookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val syncableAccount: BSyncableAccount,
  private val enable: Boolean
) : BServiceOp<BookmarkSyncEnableResult>(logger) {

  override fun runActual(): BookmarkSyncEnableResult {
    val accountId = this.syncableAccount.account.id

    this.logger.debug(
      "[{}]: {} syncing for account {}",
      this.profile.id.uuid,
      if (this.enable) "enabling" else "disabling",
      accountId.uuid
    )

    this.accountsSyncChanging.add(accountId)

    try {
      this.httpCalls.syncingEnable(
        settingsURI = this.syncableAccount.settingsURI,
        credentials = this.syncableAccount.credentials,
        enabled = this.enable
      )

      this.syncableAccount.account.setPreferences(
        this.syncableAccount.account.preferences.copy(bookmarkSyncingPermitted = this.enable)
      )

      val status = when (this.enable) {
        true -> BookmarkSyncEnableResult.SYNC_ENABLED
        false -> BookmarkSyncEnableResult.SYNC_DISABLED
      }

      this.accountsSyncChanging.remove(accountId)
      this.bookmarkEventsOut.onNext(
        BookmarkEvent.BookmarkSyncSettingChanged(
          accountID = accountId,
          status = BookmarkSyncEnableStatus.Idle(accountId, status)
        )
      )

      return status
    } finally {
      /*
       * Redundantly ensure the account has been removed from the changing set.
       */

      this.accountsSyncChanging.remove(accountId)
    }
  }
}
