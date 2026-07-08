package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.net.URI

/**
 * The book is on hold.
 */

data class OPDSAvailabilityHeld constructor(
  val startDate: DateTime?,
  val position: Int?,
  override val endDate: DateTime?,
  /**
   * @return A URI for revoking the hold, if any
   */

  val revoke: URI?
) : OPDSAvailabilityType {
  override fun toString(): String {
    val fmt = ISODateTimeFormat.dateTime()
    val b = StringBuilder(256)
    b.append("[OPDSAvailabilityHeld position=")
    b.append(this.position)
    b.append(" start_date=")
    if (this.startDate != null) {
      b.append(fmt.print(this.startDate))
    }
    b.append(" end_date=")
    if (this.endDate != null) {
      b.append(fmt.print(this.endDate))
    }
    b.append(" revoke=")
    b.append(this.revoke)
    b.append("]")
    return b.toString()
  }

  companion object {
    private const val serialVersionUID = 1L

    /**
     * @param startDate The start date (if known)
     * @param position The queue position
     * @param endDate The end date (if known)
     * @param revoke An optional revocation link for the hold
     * @return A value that states that a book is on hold
     */

    @JvmStatic
    operator fun get(
      startDate: DateTime?,
      position: Int?,
      endDate: DateTime?,
      revoke: URI?
    ): OPDSAvailabilityHeld =
      OPDSAvailabilityHeld(
        startDate = startDate,
        position = position,
        endDate = endDate,
        revoke = revoke
      )
  }
}
