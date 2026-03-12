package org.nypl.simplified.buildconfig.api

/**
 * Configuration values related to the catalog.
 */

interface BuildConfigurationCatalogType {

  /**
   * @return `true` if book detail pages should display status messages
   */

  val showDebugBookDetailStatus: Boolean
}
