package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.net.URI

/**
 * The book is held and is ready to be checked out now.
 */

data class OPDSAvailabilityHeldReady constructor(
  override val endDate: DateTime?,
  val revoke: URI?
) : OPDSAvailabilityType {
  override fun toString(): String {
    val fmt = ISODateTimeFormat.dateTime()
    val b = StringBuilder(128)
    b.append("[OPDSAvailabilityHeldReady end_date=")
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
     * @param endDate The end date (if known)
     * @param revoke The reservation revocation link, if any
     *
     * @return A value that states that a book is on hold
     */

    @JvmStatic
    fun get(
      endDate: DateTime?,
      revoke: URI?
    ): OPDSAvailabilityHeldReady =
      OPDSAvailabilityHeldReady(
        endDate = endDate,
        revoke = revoke
      )
  }
}
