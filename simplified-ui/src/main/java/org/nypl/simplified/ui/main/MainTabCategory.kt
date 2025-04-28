package org.nypl.simplified.ui.main

import org.nypl.simplified.ui.catalog.CatalogPart
import org.nypl.simplified.ui.catalog.CatalogPart.BOOKS
import org.nypl.simplified.ui.catalog.CatalogPart.CATALOG
import org.nypl.simplified.ui.catalog.CatalogPart.HOLDS

enum class MainTabCategory {
  TAB_CATALOG,
  TAB_BOOKS,
  TAB_RESERVATIONS,
  TAB_SETTINGS;

  companion object {
    fun forPart(catalogPart: CatalogPart): MainTabCategory {
      return when (catalogPart) {
        CATALOG -> TAB_CATALOG
        BOOKS -> TAB_BOOKS
        HOLDS -> TAB_RESERVATIONS
      }
    }
  }
}
