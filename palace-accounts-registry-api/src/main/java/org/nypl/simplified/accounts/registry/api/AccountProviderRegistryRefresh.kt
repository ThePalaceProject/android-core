package org.nypl.simplified.accounts.registry.api

/**
 * A request to refresh the registry.
 */

data class AccountProviderRegistryRefresh(
  val clearBeforeRefresh: Boolean,
  val includeTestingLibraries: Boolean
)
