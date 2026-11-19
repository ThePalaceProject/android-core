package org.nypl.simplified.books.time.tracking.api

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit

/**
 * The canonical time representations used when persisting time tracking data.
 *
 * Two representations are used, because the fields served different purposes:
 *
 * The fields that are read back and sent over the network (the span start and end times, and the
 * entry and event minutes) are stored with their original offset and sub-second precision intact.
 * They are rendered with [OffsetDateTime.toString], which round-trips losslessly through
 * [parse], so that the values sent to the server are identical to the times that the player
 * reported.
 *
 * The fields that are used only in database time comparisons (the span receive time, and the event
 * time) are normalized to UTC and truncated to whole seconds. They are rendered with a fixed-width
 * explicit offset, ie, `YYYY-MM-ddTHH:mm:ss+00:00`. The fixed width and fixed offset ensure that
 * lexicographic ordering of the stored values is equivalent to chronological ordering, which is
 * what the database `WHERE ... < ?` comparisons rely upon.
 */
object TimeTrackingTime {
  private val utcFormatter =
    DateTimeFormatterBuilder()
      .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
      .appendOffset("+HH:MM", "+00:00")
      .toFormatter()

  /**
   * Format a time for storage, preserving its offset and sub-second precision. The result
   * round-trips losslessly through [parse].
   *
   * @param time The time
   *
   * @return The formatted time
   */
  fun format(time: OffsetDateTime): String = time.toString()

  /**
   * Format a time for storage as a UTC time at whole-second granularity, suitable for use in
   * database time comparisons.
   *
   * @param time The time
   *
   * @return The formatted time
   */
  fun formatUTC(time: OffsetDateTime): String =
    time
      .withOffsetSameInstant(ZoneOffset.UTC)
      .truncatedTo(ChronoUnit.SECONDS)
      .format(utcFormatter)

  /**
   * Parse a stored time.
   *
   * @param text The stored time
   *
   * @return The time
   */
  fun parse(text: String): OffsetDateTime = OffsetDateTime.parse(text)
}
