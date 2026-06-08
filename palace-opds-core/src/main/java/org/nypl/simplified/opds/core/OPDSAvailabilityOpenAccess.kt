package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import java.net.URI

/**
 * The book is public domain.
 */

data class OPDSAvailabilityOpenAccess(
  override val endDate: DateTime?,
  /**
   * @return The revocation link, if any
   */

  val revoke: URI?
) : OPDSAvailabilityType {

  override fun toString(): String {
    val sb = StringBuilder("OPDSAvailabilityOpenAccess{")
    sb.append("revoke=")
      .append(revoke)
    sb.append('}')
    return sb.toString()
  }

  companion object {
    /**
     * @param revoke The revocation link, if any
     *
     * @return An "open access" availability value
     */

    @JvmStatic
    operator fun get(
      revoke: URI?
    ): OPDSAvailabilityOpenAccess {
      return OPDSAvailabilityOpenAccess(endDate = null, revoke = revoke)
    }
  }
}
