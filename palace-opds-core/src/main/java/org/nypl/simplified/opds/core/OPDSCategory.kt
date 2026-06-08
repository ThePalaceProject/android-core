package org.nypl.simplified.opds.core

import java.io.Serializable

/**
 * An OPDS/Atom category.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4287#section-4.2.2">RFC4287 categories</a>
 */
data class OPDSCategory(

  /**
   * The term.
   */
  val term: String,

  /**
   * The scheme.
   */
  val scheme: String,

  /**
   * The label.
   */
  val label: String?

) : Serializable {

  /**
   * The label, or if one is not defined, the term.
   */
  val effectiveLabel: String
    get() = this.label ?: this.term
}
