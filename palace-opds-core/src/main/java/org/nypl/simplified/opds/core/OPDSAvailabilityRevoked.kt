package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import java.net.URI

/**
 * The has been revoked via whatever DRM system it uses, but the server has yet
 * to be notified of this fact.
 */
class OPDSAvailabilityRevoked private constructor(
  override val endDate: DateTime?,
  val revoke: URI
) : OPDSAvailabilityType {
  override fun toString(): String {
    val sb = StringBuilder("OPDSAvailabilityRevoked{")
    sb.append("revoke=").append(this.revoke)
    sb.append('}')
    return sb.toString()
  }

  companion object {
    /**
     * @param revoke The revocation link
     * @return A "revoked" availability value
     */
    @JvmStatic
    fun get(revoke: URI): OPDSAvailabilityRevoked = OPDSAvailabilityRevoked(endDate = null, revoke = revoke)
  }
}
