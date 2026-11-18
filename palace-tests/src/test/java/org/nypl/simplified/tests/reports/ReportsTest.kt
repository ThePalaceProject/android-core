package org.nypl.simplified.tests.reports

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.reports.Reports
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile

class ReportsTest {

  /**
   * The time tracking data must be included in the report archive in the same compression pass
   * as the log files. If the in-memory entries are written after the archive is closed, the
   * archive's central directory lists only the appended entries, and the log files (written
   * first) are unreachable to readers. This test asserts that every entry is present in the
   * final archive and that the contents of each entry round-trip.
   */

  @Test
  fun testCompressZipfileContainsFilesAndInMemoryEntries(@TempDir directory: Path) {
    val logs =
      directory.resolve("logs")
    logs.toFile().mkdirs()
    File(logs.toFile(), "app.log").writeText("line one\nline two\n")

    val output =
      directory.resolve("report.zip").toFile()

    val csvContent =
      "time,operation\n2024-10-15T00:00:00+00:00,TimeTrackingEntryCreated\n"
    val entries =
      mapOf(
        Pair("time_tracking/time_tracking_events.csv", csvContent),
      )

    Reports.compressZipfile(
      sources = listOf(logs.toFile()),
      outputFile = output,
      inMemoryEntries = entries,
    )

    ZipFile(output).use { zip ->
      val names =
        zip
          .entries()
          .asSequence()
          .map { e -> e.name }
          .toList()

      assertTrue(names.contains("app.log"))
      assertTrue(names.contains("time_tracking/time_tracking_events.csv"))

      val logContent =
        zip
          .getInputStream(zip.getEntry("app.log"))
          .bufferedReader()
          .readText()
      assertEquals("line one\nline two\n", logContent)

      val csvRead =
        zip
          .getInputStream(zip.getEntry("time_tracking/time_tracking_events.csv"))
          .bufferedReader()
          .readText()
      assertEquals(csvContent, csvRead)
    }
  }

  @Test
  fun testCompressZipfileWithNoInMemoryEntries(@TempDir directory: Path) {
    val logs =
      directory.resolve("logs")
    logs.toFile().mkdirs()
    File(logs.toFile(), "app.log").writeText("only\n")

    val output =
      directory.resolve("report.zip").toFile()

    Reports.compressZipfile(
      sources = listOf(logs.toFile()),
      outputFile = output,
      inMemoryEntries = emptyMap(),
    )

    ZipFile(output).use { zip ->
      val names =
        zip
          .entries()
          .asSequence()
          .map { e -> e.name }
          .toList()
      assertEquals(listOf("app.log"), names)
    }
  }
}
