package org.nypl.simplified.ui.catalog

/**
 * The "part" of the catalog that a particular fragment is interested in.
 */

enum class CatalogPart {

  /**
   * The main catalog (OPDS feeds and entries on a remote server).
   */

  CATALOG,

  /**
   * The "my books" feed.
   */

  BOOKS,

  /**
   * The "my holds" feed.
   */

  HOLDS
}
