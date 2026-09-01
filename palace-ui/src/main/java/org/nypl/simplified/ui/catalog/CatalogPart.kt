package org.nypl.simplified.ui.catalog

/**
 * The "part" of the catalog that a particular fragment is interested in.
 */

enum class CatalogPart {
  /**
   * The main catalog (OPDS feeds and entries on a remote server).
   */

  CATALOG {
    override val requiresNetwork: Boolean = true
  },

  /**
   * The "my books" feed.
   */

  BOOKS {
    override val requiresNetwork: Boolean = false
  },

  /**
   * The "my holds" feed.
   */

  HOLDS {
    override val requiresNetwork: Boolean = true
  };

  abstract val requiresNetwork: Boolean
}
