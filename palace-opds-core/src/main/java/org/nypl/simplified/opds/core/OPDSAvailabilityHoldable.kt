package org.nypl.simplified.opds.core

import org.joda.time.DateTime

/**
 * The book is not available for borrowing but is available to place on hold.
 */
class OPDSAvailabilityHoldable private constructor() : OPDSAvailabilityType {
  override val endDate: DateTime? = null

  override fun toString(): String {
    return "[OPDSAvailabilityHoldable]"
  }

  companion object {
    @JvmStatic
    private val INSTANCE: OPDSAvailabilityHoldable = OPDSAvailabilityHoldable()

    /**
     * @return An availability value stating that a book is available for hold
     */
    @JvmStatic
    fun get(): OPDSAvailabilityHoldable {
      return INSTANCE
    }
  }
}
