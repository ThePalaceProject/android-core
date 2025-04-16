package org.nypl.simplified.ui.catalog.saml20

/**
 * Events raised during the SAML login process.
 */

sealed class CatalogSAML20WebClientEvent {

  /**
   * The web view client is ready for use. The login page should not be loaded until this event has
   * fired.
   */

  object WebViewClientReady : CatalogSAML20WebClientEvent()

  /**
   * The login succeeded.
   */

  object Succeeded : CatalogSAML20WebClientEvent()
}
