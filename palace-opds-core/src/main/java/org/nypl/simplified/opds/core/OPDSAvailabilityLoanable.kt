package org.nypl.simplified.opds.core

import org.joda.time.DateTime

/**
 * The book is available for borrowing.
 */
class OPDSAvailabilityLoanable private constructor() : OPDSAvailabilityType {
  override val endDate: DateTime? = null

  override fun toString(): String {
    return "[OPDSAvailabilityLoanable]"
  }

  companion object {
    @JvmStatic
    private val INSTANCE: OPDSAvailabilityLoanable =
      OPDSAvailabilityLoanable()

    /**
     * @return An availability value stating that a book is available for loan
     */
    @JvmStatic
    fun get(): OPDSAvailabilityLoanable {
      return INSTANCE
    }
  }
}
