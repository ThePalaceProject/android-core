package org.nypl.simplified.books.reader.bookmarks.internal

import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableResult
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableStatus
import org.slf4j.Logger

/**
 * An operation that enables account syncing by sending a request to the server to enable
 * the preference in the patron's user profile.
 */

internal class RBServiceOpEnableSync(
  logger: Logger,
  private val accountsSyncChanging: MutableSet<AccountID>,
  private val bookmarkEventsOut: Subject<ReaderBookmarkEvent>,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val syncableAccount: RBSyncableAccount,
  private val enable: Boolean
) : RBServiceOp<ReaderBookmarkSyncEnableResult>(logger) {

  override fun runActual(): ReaderBookmarkSyncEnableResult {
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
        true -> ReaderBookmarkSyncEnableResult.SYNC_ENABLED
        false -> ReaderBookmarkSyncEnableResult.SYNC_DISABLED
      }

      this.accountsSyncChanging.remove(accountId)
      this.bookmarkEventsOut.onNext(
        ReaderBookmarkEvent.ReaderBookmarkSyncSettingChanged(
          accountID = accountId,
          status = ReaderBookmarkSyncEnableStatus.Idle(accountId, status)
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
