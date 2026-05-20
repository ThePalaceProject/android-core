package org.nypl.simplified.accounts.registry.api

import org.nypl.simplified.taskrecorder.api.TaskResult

/**
 * The status of the account provider registry.
 */

sealed class AccountProviderRegistryStatus {

  /**
   * The account provider registry is idle.
   *
   * @param lastUpdateAffected The number of catalogs affected by the most recent update
   */

  data class Idle(
    val lastUpdateAffected: Int
  ) : AccountProviderRegistryStatus()

  /**
   * The account provider registry is currently loading.
   */

  data object Loading : AccountProviderRegistryStatus()

  /**
   * The account provider registry is currently refreshing.
   */

  data class Refreshing(
    val kind: String,
    val progress: Double?
  ) : AccountProviderRegistryStatus() {
    val progressPercent: Double?
      get() = this.progress?.times(100.0)
  }

  /**
   * The most recent refresh operation failed.
   */

  data class Failed(
    val result: TaskResult<*>
  ) : AccountProviderRegistryStatus()
}
