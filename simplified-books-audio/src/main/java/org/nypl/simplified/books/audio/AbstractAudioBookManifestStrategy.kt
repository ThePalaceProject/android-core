package org.nypl.simplified.books.audio

import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseCheckResult
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckResult
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.parser.api.ParseError
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.parser.api.ParseWarning
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.net.URI

/**
 * An audio book manifest strategy that:
 * 1. Fulfills the manifest, and fails if the manifest cannot be fulfilled.
 * 2. Parses the fulfilled manifest, and fails if the manifest cannot be parsed.
 * 3. Performs any requested license checks on the fulfilled manifest, and fails if any license
 *    checks fail.
 */

abstract class AbstractAudioBookManifestStrategy(
  private val request: AudioBookManifestRequest
) : AudioBookManifestStrategyType {

  private val logger =
    LoggerFactory.getLogger(AbstractAudioBookManifestStrategy::class.java)

  protected val eventSubject =
    PublishSubject.create<String>()

  override val events: Observable<String> =
    this.eventSubject

  /**
   * Fulfill the manifest. Subclasses must implement this method to obtain the manifest.
   */

  abstract fun fulfill(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType>

  override fun execute(): TaskResult<AudioBookManifestData> {
    val taskRecorder = TaskRecorder.create()

    try {
      val fulfillResult = this.fulfill(taskRecorder)

      if (fulfillResult is PlayerResult.Failure) {
        taskRecorder.currentStepFailed(
          message = fulfillResult.failure.message,
          errorCode = formatServerData(
            message = fulfillResult.failure.message,
            serverData = fulfillResult.failure.serverData
          )
        )
        return taskRecorder.finishFailure()
      }

      taskRecorder.beginNewStep("Parsing manifest…")
      val (contentType, downloadBytes) = (fulfillResult as PlayerResult.Success).result
      val parseResult = this.parseManifest(this.request.targetURI, downloadBytes)
      if (parseResult is ParseResult.Failure) {
        taskRecorder.currentStepFailed(
          formatParseErrorsAndWarnings(parseResult.warnings, parseResult.errors),
          ""
        )
        return taskRecorder.finishFailure()
      }

      taskRecorder.beginNewStep("Checking license…")
      val (_, parsedManifest) = parseResult as ParseResult.Success
      val checkResult = this.checkManifest(parsedManifest)
      if (!checkResult.checkSucceeded()) {
        taskRecorder.currentStepFailed(
          formatLicenseCheckStatuses(checkResult.checkStatuses),
          ""
        )
        return taskRecorder.finishFailure()
      }

      return this.finish(
        parsedManifest = parsedManifest,
        downloadBytes = downloadBytes,
        contentType = contentType,
        taskRecorder = taskRecorder
      )
    } catch (e: Exception) {
      this.logger.error("error during fulfillment: ", e)
      val message = e.message ?: e.javaClass.name
      taskRecorder.currentStepFailedAppending(message, message, e)
      return taskRecorder.finishFailure()
    }
  }

  private fun formatLicenseCheckStatuses(statuses: List<SingleLicenseCheckResult>): String {
    return buildString {
      this.append("One or more license checks failed.")
      this.append('\n')

      for (status in statuses) {
        this.append(status.shortName)
        this.append(": ")
        this.append(status.message)
        this.append('\n')
      }
    }
  }

  private fun formatParseErrorsAndWarnings(
    warnings: List<ParseWarning>,
    errors: List<ParseError>
  ): String {
    return buildString {
      this.append("Manifest parsing failed.")
      this.append('\n')

      for (warning in warnings) {
        this.append("Warning: ")
        this.append(warning.source)
        this.append(": ")
        this.append(warning.line)
        this.append(':')
        this.append(warning.column)
        this.append(": ")
        this.append(warning.message)
        this.append('\n')
      }

      for (error in errors) {
        this.append("Error: ")
        this.append(error.source)
        this.append(": ")
        this.append(error.line)
        this.append(':')
        this.append(error.column)
        this.append(": ")
        this.append(error.message)
        this.append('\n')
      }
    }
  }

  private fun formatServerData(
    message: String,
    serverData: ManifestFulfillmentErrorType.ServerData?
  ): String {
    return buildString {
      this.append(message)
      this.append('\n')

      if (serverData != null) {
        this.append("Server URI: ")
        this.append(serverData.uri)
        this.append('\n')

        this.append("Server status: ")
        this.append(serverData.code)
        this.append('\n')

        if (serverData.receivedContentType == "application/api-problem+json") {
          val parser =
            request.problemReportParsers.createParser(
              uri = serverData.uri.toString(),
              stream = serverData.receivedBody.inputStream()
            )

          try {
            val report = parser.execute()
            this.append("Status: ")
            this.append(report.status)
            this.append('\n')

            this.append("Type: ")
            this.append(report.type)
            this.append('\n')

            this.append("Title: ")
            this.append(report.title)
            this.append('\n')

            this.append("Detail: ")
            this.append(report.detail)
            this.append('\n')

            logger.error("status: {}", report.status)
            logger.error("type:   {}", report.type)
            logger.error("title:  {}", report.title)
            logger.error("detail: {}", report.detail)
          } catch (e: Exception) {
            logger.error("unparseable problem report: ", e)
          }
        }
      }
    }
  }

  private fun finish(
    parsedManifest: PlayerManifest,
    downloadBytes: ByteArray,
    contentType: MIMEType,
    taskRecorder: TaskRecorderType
  ): TaskResult<AudioBookManifestData> {
    return taskRecorder.finishSuccess(
      AudioBookManifestData(
        manifest = parsedManifest,
        fulfilled = ManifestFulfilled(
          contentType = contentType,
          data = downloadBytes
        )
      )
    )
  }

  /**
   * Attempt to parse a manifest file.
   */

  private fun parseManifest(
    source: URI,
    data: ByteArray
  ): ParseResult<PlayerManifest> {
    this.logger.debug("parseManifest")
    return this.request.manifestParsers.parse(
      uri = source,
      streams = data,
      extensions = this.request.extensions
    )
  }

  /**
   * Attempt to perform any required license checks on the manifest.
   */

  private fun checkManifest(
    manifest: PlayerManifest
  ): LicenseCheckResult {
    this.logger.debug("checkManifest")

    val check =
      LicenseChecks.createLicenseCheck(
        LicenseCheckParameters(
          manifest = manifest,
          userAgent = this.request.userAgent,
          checks = this.request.licenseChecks,
          cacheDirectory = this.request.cacheDirectory
        )
      )

    val checkSubscription =
      check.events.subscribe { event ->
        this.onLicenseCheckEvent(event)
      }

    try {
      return check.execute()
    } finally {
      checkSubscription.unsubscribe()
    }
  }

  private fun onLicenseCheckEvent(event: SingleLicenseCheckStatus) {
    this.logger.debug("onLicenseCheckEvent: {}: {}", event.source, event.message)
    this.eventSubject.onNext(event.message)
  }
}
