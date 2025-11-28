package org.nypl.simplified.buildconfig.api

/**
 * Configuration values related to the catalog.
 */

interface BuildConfigurationCatalogType {

  /**
   * @return `true` if book detail pages should display status messages
   */

  val showDebugBookDetailStatus: Boolean

  /**
   * Should books from _all_ accounts be shown in the Books views?
   */

  val showBooksFromAllAccounts: Boolean
}
