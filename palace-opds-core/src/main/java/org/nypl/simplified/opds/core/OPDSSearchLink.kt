package org.nypl.simplified.opds.core

import java.net.URI

/**
 * The type of search links.
 *
 * @property type The type of search document
 * @property uri The search document URI
 */
data class OPDSSearchLink(
  /**
   * The type of search document.
   */
  val type: String,
  /**
   * The search document URI.
   */
  val uri: URI
)
