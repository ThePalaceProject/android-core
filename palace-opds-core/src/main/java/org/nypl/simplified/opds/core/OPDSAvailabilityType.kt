package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import java.io.Serializable

/**
 * The type of book availability.
 *
 * OPDS does not have a standard way
 * to signal the availability of a given item, so this implementation determines
 * availability based on extra non-OPDS information added to the feed.
 */
sealed interface OPDSAvailabilityType : Serializable {
  /**
   * The date when the availability expires, if there is one
   *
   * @return end_date
   */
  val endDate: DateTime?
}
