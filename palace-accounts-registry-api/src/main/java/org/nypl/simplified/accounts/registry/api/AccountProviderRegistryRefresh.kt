package org.nypl.simplified.accounts.registry.api

/**
 * A request to refresh the registry.
 */

sealed interface AccountProviderRegistryRefresh {
  val includeTestingLibraries: Boolean

  /**
   * Perform a full refresh, walking the full crawlable registry feed.
   */

  data class Full(
    val clearBeforeRefresh: Boolean,
    override val includeTestingLibraries: Boolean
  ) : AccountProviderRegistryRefresh

  /**
   * Perform an incremental refresh, fetching only those libraries that have changed since the
   * last refresh.
   */

  data class Incremental(
    override val includeTestingLibraries: Boolean
  ) : AccountProviderRegistryRefresh
}
