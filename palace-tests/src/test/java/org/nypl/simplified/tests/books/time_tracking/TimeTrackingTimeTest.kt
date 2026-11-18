package org.nypl.simplified.tests.books.time_tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import java.time.OffsetDateTime

/**
 * Tests for the two-tier time formatting used for persisting time tracking data.
 *
 * The wire format ([TimeTrackingTime.format]) must round-trip losslessly, preserving the
 * original offset and sub-second precision, because the values are sent to the server as-is.
 * The comparison format ([TimeTrackingTime.formatUTC]) must be fixed-width and fixed-offset, so
 * that lexicographic ordering of the stored text is equivalent to chronological ordering of the
 * instants.
 */
class TimeTrackingTimeTest {
  @Test
  fun testFormatRoundTripsPreservingOffsetAndPrecision() {
    val time =
      OffsetDateTime.parse("2024-10-15T05:30:45.123+05:30")

    val text =
      TimeTrackingTime.format(time)

    assertEquals("2024-10-15T05:30:45.123+05:30", text)
    assertEquals(time, TimeTrackingTime.parse(text))
  }

  @Test
  fun testFormatRoundTripsUTC() {
    val time =
      OffsetDateTime.parse("2024-10-15T00:00:00Z")

    val text =
      TimeTrackingTime.format(time)

    assertEquals("2024-10-15T00:00Z", text)
    assertEquals(time, TimeTrackingTime.parse(text))
  }

  @Test
  fun testFormatUTCNormalizesOffsetAndTruncatesSeconds() {
    val time =
      OffsetDateTime.parse("2024-10-15T05:30:45.999+05:30")

    val text =
      TimeTrackingTime.formatUTC(time)

    assertEquals("2024-10-15T00:00:45+00:00", text)
  }

  @Test
  fun testFormatUTCIsFixedWidthAndLexicographicallyOrdered() {
    val times =
      listOf(
        OffsetDateTime.parse("2024-01-02T03:04:05Z"),
        OffsetDateTime.parse("2024-12-31T23:59:59+05:30"),
      )
    val texts =
      times.map { TimeTrackingTime.formatUTC(it) }

    /*
     * Both values must be the same width, despite the different month, day, hour, and offset
     * values.
     */

    for (text in texts) {
      assertEquals(25, text.length)
    }

    /*
     * Lexicographic ordering must agree with chronological ordering; the second instant (in
     * UTC, 2025-01-01T18:29:59Z) is after the first.
     */

    assertTrue(texts[0] < texts[1])
  }
}
