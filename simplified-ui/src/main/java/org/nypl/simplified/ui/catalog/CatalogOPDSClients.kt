package org.nypl.simplified.ui.catalog

import org.thepalaceproject.opds.client.OPDSClientType

data class CatalogOPDSClients(
  val mainClient: OPDSClientType,
  val booksClient: OPDSClientType,
  val holdsClient: OPDSClientType
) : AutoCloseable {

  fun clientFor(
    part: CatalogPart
  ): OPDSClientType {
    return when (part) {
      CatalogPart.CATALOG -> this.mainClient
      CatalogPart.BOOKS -> this.booksClient
      CatalogPart.HOLDS -> this.holdsClient
    }
  }

  override fun close() {
    this.mainClient.close()
    this.booksClient.close()
    this.holdsClient.close()
  }
}
