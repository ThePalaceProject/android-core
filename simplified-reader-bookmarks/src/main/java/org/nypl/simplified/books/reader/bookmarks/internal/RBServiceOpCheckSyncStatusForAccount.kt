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

    this.checkSyncingIsEnabledForAccount(this.profile, this.syncableAccount)
  }

  private fun checkSyncingIsEnabledForAccount(
    profile: ProfileReadableType,
    account: RBSyncableAccount
  ) {

    try {
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
      } else {
        this.logger.debug(
          "[{}]: account {} does not have syncing enabled",
          profile.id.uuid,
          account.account.id
        )
      }
    } catch (e: Exception) {
      this.logger.error("error checking account for syncing: ", e)
    }
  }
}
