package org.librarysimplified.reports

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import org.apache.commons.io.IOUtils
import org.librarysimplified.reports.Reports.Result.RaisedException
import org.librarysimplified.reports.Reports.Result.Sent
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Functions to send reports.
 */

object Reports {
  @Volatile
  var reportLibrary: String = "Unknown"

  @Volatile
  var reportTimeTracking: () -> Map<String, String> = { emptyMap() }

  @Volatile
  var reportScreenDPI: Int = 0

  @Volatile
  var reportScreenHeight: Int = 0

  @Volatile
  var reportAppCommit: String =
    "Unknown"

  @Volatile
  var reportAppVersion: String =
    "Unknown"

  val reportPlatform: String =
    "Android"

  private val logger =
    LoggerFactory.getLogger(Reports::class.java)

  /**
   * The result of trying to send.
   */

  sealed class Result {
    /**
     * An attempt was made to send.
     */

    object Sent : Result()

    /**
     * There were no files to send.
     */

    object NoFiles : Result()

    /**
     * An exception was raised.
     */

    data class RaisedException(
      val exception: Exception,
    ) : Result()
  }

  /**
   * Try to send a report using the default settings.
   */

  @JvmStatic
  fun sendReportsDefault(
    context: Context,
    address: String,
    body: String,
  ): Result =
    this.sendReport(
      context = context,
      address = address,
      body = body,
    )

  /**
   * Try to send a report.
   */

  @JvmStatic
  fun sendReport(
    context: Context,
    address: String,
    body: String,
  ): Result {
    this.logger.debug("preparing report")

    try {
      val files =
        listOf(
          File(context.cacheDir, "logs"),
        )

      val reportZip = File(context.cacheDir, "report.zip")
      reportZip.delete()
      val timeTrackingData =
        this.reportTimeTracking()
      compressZipfile(files, reportZip, timeTrackingData)

      val zipContentURI =
        this.mapFileToContentURI(context, reportZip)

      val intent =
        Intent(Intent.ACTION_SEND).apply {
          this.type = "message/rfc822"
          this.putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
          this.putExtra(Intent.EXTRA_SUBJECT, "Issue Report from The Palace Project App")
          this.putExtra(Intent.EXTRA_TEXT, this@Reports.decorateBodyText(body))
          this.putExtra(Intent.EXTRA_STREAM, zipContentURI)
          this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      return Sent
    } catch (e: Exception) {
      this.logger.debug("failed to send report: ", e)
      return RaisedException(e)
    }
  }

  fun decorateBodyText(body: String): String {
    val bodyLines = ArrayList<String>()
    bodyLines.add("Please describe your issue:")
    bodyLines.add("\n")
    bodyLines.add("\n")
    bodyLines.add(body)
    bodyLines.add("\n")
    bodyLines.add("\n")
    bodyLines.add("--")
    bodyLines.add("Platform: ${this.reportPlatform}")
    bodyLines.add("Height: ${this.reportScreenHeight} (${this.reportScreenDPI} DPI)")
    bodyLines.add("Palace Version: ${this.reportAppVersion}")
    bodyLines.add("Palace Commit: ${this.reportAppCommit}")
    bodyLines.add("Library: ${this.reportLibrary}")
    bodyLines.add("Device Patch Date: ${Build.VERSION.SECURITY_PATCH}")
    bodyLines.add("Device API Level: ${Build.VERSION.SDK_INT}")
    bodyLines.add("Device Manufacturer: ${Build.MANUFACTURER}")
    bodyLines.add("Device Brand: ${Build.BRAND}")
    bodyLines.add("Device Model: ${Build.MODEL} (${Build.DEVICE})")
    return bodyLines.joinToString("\n")
  }

  private fun mapFileToContentURI(
    context: Context,
    file: File,
  ) = FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file)

  /**
   * Compress the given source files into a zip file, and additionally emit the given
   * in-memory entries into the same archive.
   *
   * The in-memory entries are written within the same [ZipOutputStream] session as the files.
   * This is required because there is no supported way to append entries to an existing zip
   * archive; opening an existing file for append and writing new entries would produce an
   * archive whose central directory lists only the appended entries, making the original
   * contents unreachable to readers.
   *
   * @param sources The files to compress
   * @param outputFile The output zip file
   * @param inMemoryEntries The in-memory entries (name to text content) to include
   */

  @JvmStatic
  @Throws(IOException::class)
  fun compressZipfile(
    sources: List<File>,
    outputFile: File,
    inMemoryEntries: Map<String, String>,
  ) {
    ZipOutputStream(FileOutputStream(outputFile)).use { zipFile ->
      for (file in sources) {
        compressToZipFile(file, zipFile)
      }
      for ((name, content) in inMemoryEntries) {
        val entry = ZipEntry(name)
        zipFile.putNextEntry(entry)
        zipFile.write(content.toByteArray(Charsets.UTF_8))
        zipFile.closeEntry()
      }
    }
  }

  @JvmStatic
  @Throws(IOException::class)
  private fun compressToZipFile(
    source: File,
    zipFile: ZipOutputStream,
  ) {
    if (source.isFile) {
      this.logger.debug("Compress {}", source)

      val entry = ZipEntry(source.name)
      zipFile.putNextEntry(entry)
      FileInputStream(source).use { input ->
        IOUtils.copy(input, zipFile)
      }
      return
    }

    for (file in source.listFiles() ?: arrayOf()) {
      compressToZipFile(file, zipFile)
    }
  }
}
