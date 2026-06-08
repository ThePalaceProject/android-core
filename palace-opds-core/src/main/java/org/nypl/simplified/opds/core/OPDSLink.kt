package org.nypl.simplified.opds.core

import java.math.BigInteger
import java.net.URI

/**
 * A generic OPDS link. These are typically used in authentication documents.
 */

data class OPDSLink(

  /**
   * The optional hash of the target content.
   */
  val hash: String?,

  /**
   * The URI of the target content.
   */
  val href: URI,

  /**
   * The optional type of the target content.
   */
  val type: String?,

  /**
   * The optional length of the target content.
   */
  val length: BigInteger?
)
