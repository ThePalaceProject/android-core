package org.nypl.simplified.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.Logger

/**
 * An operation that checks the sync status of all accounts that support syncing
 * in a given profile.
 */

internal class BServiceOpCheckSyncStatusForProfile(
  logger: Logger,
  private val httpCalls: BookmarkHTTPCallsType,
  private val profile: ProfileReadableType
) : BServiceOp<Unit>(logger) {

  override fun runActual() {
    this.logger.debug("[{}]: checking account sync values", this.profile.id.uuid)

    return this.getPossiblySyncableAccounts(this.profile)
      .forEach(this::checkSyncingIsEnabledForEntry)
  }

  private fun checkSyncingIsEnabledForEntry(
    entry: Map.Entry<AccountID, BSyncableAccount?>
  ) {
    val account = entry.value ?: return
    return BServiceOpCheckSyncStatusForAccount(
      logger = this.logger,
      httpCalls = this.httpCalls,
      profile = this.profile,
      syncableAccount = account
    ).call()
  }

  private fun getPossiblySyncableAccounts(
    profile: ProfileReadableType
  ): Map<AccountID, BSyncableAccount?> {
    this.logger.debug("[{}]: querying accounts for syncing", profile.id.uuid)
    return profile.accounts().mapValues { entry ->
      BSyncableAccount.ofAccount(entry.value)
    }
  }
}
