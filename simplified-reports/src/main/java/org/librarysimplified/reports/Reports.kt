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
      val exception: Exception
    ) : Result()
  }

  /**
   * Try to send a report using the default settings.
   */

  @JvmStatic
  fun sendReportsDefault(
    context: Context,
    address: String,
    body: String
  ): Result {
    return this.sendReport(
      context = context,
      address = address,
      body = body
    )
  }

  /**
   * Try to send a report.
   */

  @JvmStatic
  fun sendReport(
    context: Context,
    address: String,
    body: String
  ): Result {
    this.logger.debug("preparing report")

    try {
      val files = listOf(
        File(File(context.filesDir, "v4.0"), "time_tracking"),
        File(context.cacheDir, "logs")
      )

      val reportZip = File(context.cacheDir, "report.zip")
      reportZip.delete()
      compressZipfile(files, reportZip)

      val zipContentURI =
        this.mapFileToContentURI(context, reportZip)

      val intent = Intent(Intent.ACTION_SEND).apply {
        this.type = "message/rfc822"
        this.putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        this.putExtra(Intent.EXTRA_SUBJECT, "Issue Report from The Palace Project App")
        this.putExtra(Intent.EXTRA_TEXT, this@Reports.extendBody(body))
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

  private fun extendBody(
    body: String
  ): String {
    val bodyLines = ArrayList<String>()
    bodyLines.add("Please describe your issue:")
    bodyLines.add("\n")
    bodyLines.add("\n")
    bodyLines.add("\n")
    bodyLines.add(body)
    bodyLines.add("\n")
    bodyLines.add("\n")
    bodyLines.add("--")
    bodyLines.add("Platform: ${this.reportPlatform}")
    bodyLines.add("OS: Android")
    bodyLines.add("Height: ${this.reportScreenHeight} (${this.reportScreenDPI} DPI)")
    bodyLines.add("Palace Version: ${this.reportAppVersion}")
    bodyLines.add("Palace Commit: ${this.reportAppCommit}")
    bodyLines.add("Library: ${this.reportLibrary}")
    bodyLines.add("Device Manufacturer: ${Build.MANUFACTURER}")
    bodyLines.add("Device Brand: ${Build.BRAND}")
    bodyLines.add("Device Model: ${Build.MODEL} (${Build.DEVICE})")
    bodyLines.add("Device FP: ${Build.FINGERPRINT}")
    return bodyLines.joinToString("\n")
  }

  private fun mapFileToContentURI(context: Context, file: File) =
    FileProvider.getUriForFile(context, context.packageName + ".fileProvider", file)

  @JvmStatic
  @Throws(IOException::class)
  private fun compressZipfile(
    sources: List<File>,
    outputFile: File
  ) {
    ZipOutputStream(FileOutputStream(outputFile)).use { zipFile ->
      for (file in sources) {
        compressToZipFile(file, zipFile)
      }
    }
  }

  @JvmStatic
  @Throws(IOException::class)
  private fun compressToZipFile(
    source: File,
    zipFile: ZipOutputStream
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
