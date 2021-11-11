package org.nypl.simplified.books.reader.bookmarks.internal

import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.slf4j.Logger

/**
 * An operation that checks the sync status of a specific account in a given profile.
 */

internal class RBServiceOpCheckSyncStatusForAccount(
  logger: Logger,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val profile: ProfileReadableType,
  private val syncableAccount: RBSyncableAccount
) : RBServiceOp<Unit>(logger) {

  override fun runActual() {
    this.logger.debug(
      "[{}]: checking sync status for account {}",
      this.profile.id.uuid,
      this.syncableAccount.account.id
    )

    val syncable =
      this.checkSyncingIsEnabledForAccount(this.profile, this.syncableAccount)

    this.syncableAccount.account.setPreferences(
      this.syncableAccount.account.preferences.copy(
        bookmarkSyncingPermitted = syncable != null
      )
    )
  }

  private fun checkSyncingIsEnabledForAccount(
    profile: ProfileReadableType,
    account: RBSyncableAccount
  ): RBSyncableAccount? {
    return try {
      this.logger.debug(
        "[{}]: checking account {} has syncing enabled",
        profile.id.uuid,
        account.account.id
      )

      if (this.httpCalls.syncingIsEnabled(account.settingsURI, account.credentials)) {
        this.logger.debug(
          "[{}]: account {} has syncing enabled",
          profile.id.uuid,
          account.account.id
        )
        account
      } else {
        this.logger.debug(
          "[{}]: account {} does not have syncing enabled",
          profile.id.uuid,
          account.account.id
        )
        null
      }
    } catch (e: Exception) {
      this.logger.error("error checking account for syncing: ", e)
      return null
    }
  }
}
