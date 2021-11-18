package org.nypl.simplified.books.reader.bookmarks.internal

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.slf4j.Logger

/**
 * An operation that checks the sync status of all accounts that support syncing
 * in a given profile.
 */

internal class RBServiceOpCheckSyncStatusForProfile(
  logger: Logger,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val profile: ProfileReadableType
) : RBServiceOp<Unit>(logger) {

  override fun runActual() {
    this.logger.debug("[{}]: checking account sync values", this.profile.id.uuid)

    return this.getPossiblySyncableAccounts(this.profile)
      .forEach(this::checkSyncingIsEnabledForEntry)
  }

  private fun checkSyncingIsEnabledForEntry(
    entry: Map.Entry<AccountID, RBSyncableAccount?>
  ) {
    val account = entry.value ?: return
    return RBServiceOpCheckSyncStatusForAccount(
      logger = this.logger,
      httpCalls = this.httpCalls,
      profile = this.profile,
      syncableAccount = account
    ).call()
  }

  private fun getPossiblySyncableAccounts(
    profile: ProfileReadableType
  ): Map<AccountID, RBSyncableAccount?> {
    this.logger.debug("[{}]: querying accounts for syncing", profile.id.uuid)
    return profile.accounts().mapValues { entry ->
      RBSyncableAccount.ofAccount(entry.value)
    }
  }
}
