package org.nypl.simplified.opds.core

import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat

/**
 * A supplier of date/time parsers.
 */
object OPDSDateParsers {
  /**
   * @return A properly configured date/time parser.
   */
  @JvmStatic
  fun dateTimeParser(): DateTimeFormatter = ISODateTimeFormat.dateTimeParser().withZoneUTC()
}
