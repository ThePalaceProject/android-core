package org.librarysimplified.reports

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

  private val logger = LoggerFactory.getLogger(Reports::class.java)

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
    subject: String,
    body: String
  ): Result {
    return this.sendReport(
      context = context,
      address = address,
      subject = subject,
      body = body
    )
  }

  @JvmStatic
  private fun isSuitableForSending(
    name: String
  ): Boolean {
    if (name.startsWith("log.txt")) {
      return true
    }
    if (name.equals("time_tracking_debug.dat")) {
      return true
    }
    if (name.startsWith("report-") && name.endsWith(".xml")) {
      return true
    }
    return false
  }

  /**
   * Try to send a report.
   */

  @JvmStatic
  fun sendReport(
    context: Context,
    address: String,
    subject: String,
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

      val contentURIs: ArrayList<Uri> = ArrayList()
      contentURIs.add(this.mapFileToContentURI(context, reportZip))

      val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        this.type = "text/plain"
        this.putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        this.putExtra(Intent.EXTRA_SUBJECT, this@Reports.extendSubject(context, subject))
        this.putExtra(Intent.EXTRA_TEXT, this@Reports.extendBody(body))
        this.putExtra(Intent.EXTRA_STREAM, contentURIs)
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
  ): ArrayList<String> {
    val bodyLines = ArrayList<String>()
    bodyLines.add(buildString {
      this.append(body)
      this.append("\n")
      this.append("--")
      this.append("\n")
      this.append("Commit: ")
      this.append(BuildConfig.SIMPLIFIED_GIT_COMMIT)
      this.append("\n")
    })
    return bodyLines
  }

  private fun extendSubject(
    context: Context,
    subject: String
  ): String {
    val pkgManager = context.packageManager
    val pkgInfo = try {
      pkgManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
      this.logger.debug("unable to retrieve package information: ", e)
      return subject
    }

    return buildString {
      this.append(subject)
      this.append(' ')
      this.append(pkgInfo.versionName)
      this.append(" (")
      this.append(pkgInfo.versionCode)
      this.append(")")
    }
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
