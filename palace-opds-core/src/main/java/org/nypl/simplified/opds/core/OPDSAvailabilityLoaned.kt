package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.net.URI

/**
 * The book is loaned out to the user.
 */

data class OPDSAvailabilityLoaned(

  /**
   * @return The start date for the loan, if any
   */

  val startDate: DateTime?,

  /**
   * @return A URI for revoking the hold, if any
   */

  val revoke: URI?,

  override val endDate: DateTime?
) : OPDSAvailabilityType {

  override fun toString(): String {
    val fmt = ISODateTimeFormat.dateTime()
    val b = StringBuilder(128)
    b.append("[OPDSAvailabilityLoaned end_date=")
    b.append(this.endDate.let { c: DateTime? -> fmt.print(c) })
    b.append(" start_date=")
    b.append(this.startDate.let { c: DateTime? -> fmt.print(c) })
    b.append(" revoke=")
    b.append(this.revoke)
    b.append("]")
    return b.toString()
  }

  companion object {
    private const val serialVersionUID = 1L

    /**
     * @param startDate The start date for the loan
     * @param endDate The end date for the loan
     * @param revoke The optional revocation link for the loan
     *
     * @return An availability value that states that the given book is loaned
     */

    @JvmStatic
    operator fun get(
      startDate: DateTime?,
      endDate: DateTime?,
      revoke: URI?
    ): OPDSAvailabilityLoaned {
      return OPDSAvailabilityLoaned(
        startDate = startDate,
        revoke = revoke,
        endDate = endDate
      )
    }
  }
}
