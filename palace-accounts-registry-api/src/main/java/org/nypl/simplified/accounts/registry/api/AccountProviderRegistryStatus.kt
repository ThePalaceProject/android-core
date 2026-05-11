package org.nypl.simplified.accounts.registry.api

import org.nypl.simplified.taskrecorder.api.TaskResult

/**
 * The status of the account provider registry.
 */

sealed class AccountProviderRegistryStatus {

  /**
   * The account provider registry is idle.
   */

  data object Idle : AccountProviderRegistryStatus()

  /**
   * The account provider registry is currently refreshing.
   */

  data class Refreshing(
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
