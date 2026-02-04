package org.nypl.simplified.books.audio

import android.app.Application
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.lcp.downloads.LCPDownloads
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseCheckResult
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentCredentialsBasic
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentCredentialsToken
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentCredentialsType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestFulfillmentStrategyProviderType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestURI.Indirect
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAParameters
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword.Password
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.parser.api.ParseError
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.parser.api.ParseWarning
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPProblemReport
import org.librarysimplified.http.downloads.LSHTTPDownloadRequest
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStepResolution
import org.readium.r2.lcp.license.model.LicenseDocument
import org.slf4j.LoggerFactory
import java.net.URI

class AudioBookStrategy(
  private val context: Application,
  private val request: AudioBookManifestRequest
) : AudioBookStrategyType {

  private val logger =
    LoggerFactory.getLogger(AudioBookStrategy::class.java)

  private val eventSubject =
    PublishSubject.create<String>()

  private val taskRecorder: TaskRecorderType =
    TaskRecorder.create()

  override val events: Observable<String> =
    this.eventSubject

  override fun execute(): TaskResult<AudioBookManifestData> {
    return try {
      if (this.request.isNetworkAvailable.invoke()) {
        this.fulfillLink()
      } else {
        this.fulfillFromFallback()
      }
    } catch (e: Exception) {
      this.logger.error("Error during fulfillment: ", e)
      val message = e.message ?: e.javaClass.name
      this.taskRecorder.currentStepFailedAppending(
        message = message,
        errorCode = message,
        exception = e,
        extraMessages = listOf()
      )
      this.taskRecorder.finishFailure()
    }
  }

  private class AsManifestFulfillment(
    val owner: AudioBookStrategy
  ) : ManifestFulfillmentStrategyType {

    private val eventSubject: PublishSubject<ManifestFulfillmentEvent> =
      PublishSubject.create()

    override val events: Observable<ManifestFulfillmentEvent>
      get() = this.eventSubject

    override fun close() {
      this.eventSubject.onComplete()
    }

    override fun execute(): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
      return when (val r = this.owner.execute()) {
        is TaskResult.Failure -> {
          PlayerResult.Failure(
            ManifestFulfillmentError(
              message = r.message,
              extraMessages =
              r.steps.filter { s -> s.resolution is TaskStepResolution.TaskStepFailed }
                .map { s -> s.resolution.message },
              serverData = null
            )
          )
        }

        is TaskResult.Success -> {
          PlayerResult.Success(r.result.fulfilled)
        }
      }
    }
  }

  override fun toManifestStrategy(): ManifestFulfillmentStrategyType {
    return AsManifestFulfillment(this)
  }

  private fun fulfillFromFallback(): TaskResult<AudioBookManifestData> {
    this.taskRecorder.beginNewStep("Loading manifest data from fallback.")

    val data = this.request.loadFallbackData.invoke()
    if (data == null) {
      this.taskRecorder.currentStepFailed(
        message = "No network is available, and no fallback data is available",
        errorCode = "errorNoFallback",
        extraMessages = listOf()
      )
      return this.taskRecorder.finishFailure()
    }

    this.taskRecorder.currentStepSucceeded("Fallback data loaded successfully.")
    return this.parseManifest(
      source = data.source,
      licenseBytes = null,
      authorization = data.authorization,
      manifestBytes = data.data
    )
  }

  private fun fulfillLink(): TaskResult<AudioBookManifestData> {
    return when (val target = this.request.target) {
      is AudioBookLink.License -> this.fulfillLinkLicense(target)
      is AudioBookLink.Manifest -> this.fulfillLinkManifest(target)
    }
  }

  private fun fulfillLinkManifest(
    target: AudioBookLink.Manifest
  ): TaskResult<AudioBookManifestData> {
    this.taskRecorder.beginNewStep("Retrieving manifest file.")

    val strategy = this.findExistingStrategy(target.target)
    strategy.events.subscribe { event -> this.eventSubject.onNext(event.message) }

    return when (val result = strategy.execute()) {
      is PlayerResult.Failure -> {
        this.recordProblemReport(result.failure.serverData?.problemReport)
        this.taskRecorder.currentStepFailed(
          message = result.failure.message,
          errorCode = "errorDownloadFailed",
          extraMessages = result.failure.extraMessages
        )
        this.taskRecorder.finishFailure()
      }

      is PlayerResult.Success -> {
        this.taskRecorder.currentStepSucceeded("Manifest retrieval succeeded.")
        this.parseManifest(
          source = target.target,
          licenseBytes = null,
          authorization = result.result.authorization,
          manifestBytes = result.result.data
        )
      }
    }
  }

  private fun fulfillLinkLicense(
    target: AudioBookLink.License
  ): TaskResult<AudioBookManifestData> {
    this.taskRecorder.beginNewStep("Retrieving license file.")

    val httpRequest =
      this.request.httpClient.newRequest(target.target)
        .setAuthorization(authorization = createAuthorization(this.request.credentials))
        .build()

    val temporaryFile =
      this.request.temporaryFile(".lcpl")

    try {
      val downloadRequest =
        LSHTTPDownloadRequest(
          request = httpRequest,
          outputFile = temporaryFile,
          onEvent = { event ->
            this.logger.debug("Download event: {}", event)
          },
          isCancelled = { false }
        )

      return when (val result = LSHTTPDownloads.download(downloadRequest)) {
        is DownloadCompletedSuccessfully -> {
          this.taskRecorder.currentStepSucceeded("Download succeeded.")
          this.taskRecorder.beginNewStep("Parsing LCP license.")

          val licenseBytes = temporaryFile.readBytes()
          when (val licenseResult = LCPDownloads.parseLicense(licenseBytes)) {
            is PlayerResult.Failure -> {
              this.taskRecorder.currentStepFailed(
                message = licenseResult.failure.message,
                errorCode = "errorLicenseParse",
                extraMessages = licenseResult.failure.extraMessages
              )
              this.recordProblemReport(licenseResult.failure.serverData?.problemReport)
              this.taskRecorder.finishFailure()
            }

            is PlayerResult.Success -> {
              this.taskRecorder.currentStepSucceeded("License parsed.")
              this.fulfillManifestFromLicense(
                licenseBytes = licenseBytes,
                license = licenseResult.result.license
              )
            }
          }
        }

        DownloadCancelled -> {
          this.taskRecorder.currentStepFailed(
            message = "Download cancelled.",
            errorCode = "cancelled",
            extraMessages = listOf()
          )
          this.recordProblemReport(result.responseStatus?.properties?.problemReport)
          this.taskRecorder.finishFailure()
        }

        is DownloadFailedExceptionally -> {
          this.taskRecorder.currentStepFailed(
            message = "Request failed.",
            errorCode = "errorException",
            result.exception,
            extraMessages = listOf()
          )
          this.recordProblemReport(result.responseStatus?.properties?.problemReport)
          this.taskRecorder.finishFailure()
        }

        is DownloadFailedServer -> {
          this.taskRecorder.currentStepFailed(
            message = "Request failed.",
            errorCode = "errorRequest",
            extraMessages = listOf()
          )
          this.recordProblemReport(result.responseStatus.properties.problemReport)
          this.taskRecorder.finishFailure()
        }

        is DownloadFailedUnacceptableMIME -> {
          this.taskRecorder.currentStepFailed(
            message = "Server returned unexpected MIME type.",
            errorCode = "errorMime",
            extraMessages = listOf()
          )
          this.recordProblemReport(result.responseStatus.properties?.problemReport)
          this.taskRecorder.finishFailure()
        }
      }
    } finally {
      temporaryFile.delete()
    }
  }

  private fun createAuthorization(
    credentials: ManifestFulfillmentCredentialsType?
  ): LSHTTPAuthorizationType? {
    return when (credentials) {
      is ManifestFulfillmentCredentialsBasic -> {
        LSHTTPAuthorizationBasic.ofUsernamePassword(
          userName = credentials.userName,
          password = credentials.password
        )
      }
      is ManifestFulfillmentCredentialsToken -> {
        LSHTTPAuthorizationBearerToken.ofToken(
          token = credentials.token
        )
      }
      null -> null
    }
  }

  private fun recordProblemReport(
    report: LSHTTPProblemReport?
  ) {
    if (report != null) {
      this.taskRecorder.addAttributes(report.toMap())
    }
  }

  private fun fulfillManifestFromLicense(
    licenseBytes: ByteArray,
    license: LicenseDocument
  ): TaskResult<AudioBookManifestData> {
    this.taskRecorder.beginNewStep("Retrieving manifest via LCP license.")

    return when (val result = LCPDownloads.downloadManifestFromPublication(
      context = this.context,
      credentials = request.credentials,
      license = license,
      receiver = { event ->
        this.logger.debug("Downloading manifest event: {}", event)
      }
    )) {
      is PlayerResult.Failure -> {
        this.recordProblemReport(result.failure.serverData?.problemReport)
        this.taskRecorder.currentStepFailed(
          message = result.failure.message,
          errorCode = "httpRequestFailed",
          extraMessages = result.failure.extraMessages
        )
        this.taskRecorder.finishFailure()
      }

      is PlayerResult.Success -> {
        this.taskRecorder.currentStepSucceeded("Successfully retrieved manifest.")
        return this.parseManifest(
          source = result.result.source,
          licenseBytes = licenseBytes,
          authorization = result.result.authorization,
          manifestBytes = result.result.data
        )
      }
    }
  }

  /**
   * Attempt to parse a manifest file.
   */

  private fun parseManifest(
    source: URI?,
    authorization: LSHTTPAuthorizationType?,
    licenseBytes: ByteArray?,
    manifestBytes: ByteArray
  ): TaskResult<AudioBookManifestData> {
    this.taskRecorder.beginNewStep("Parsing manifest.")
    return when (val result = this.request.manifestParsers.parse(
      uri = source ?: URI.create("urn:unavailable"),
      input = ManifestUnparsed(this.request.palaceID, manifestBytes),
      extensions = this.request.extensions
    )) {
      is ParseResult.Failure -> {
        this.taskRecorder.currentStepFailed(
          message = "Manifest parsing failed.",
          errorCode = "parseError",
          extraMessages =
          this.formatParseWarnings(result.warnings)
            .plus(this.formatParseErrors(result.errors))
        )
        return this.taskRecorder.finishFailure()
      }

      is ParseResult.Success -> {
        this.taskRecorder.currentStepSucceeded("Manifest parsed successfully.")
        this.taskRecorder.finishSuccess(
          AudioBookManifestData(
            result.result,
            licenseBytes = licenseBytes,
            fulfilled = ManifestFulfilled(
              source = source,
              contentType = MIMEType("text", "json", mapOf()),
              authorization = authorization,
              data = manifestBytes
            )
          )
        )
      }
    }
  }

  private fun formatParseWarnings(warnings: List<ParseWarning>): List<String> {
    return warnings.map { warning ->
      buildString {
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
    }
  }

  private fun formatParseErrors(errors: List<ParseError>): List<String> {
    return errors.map { error ->
      buildString {
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
      checkSubscription.dispose()
    }
  }

  private fun onLicenseCheckEvent(event: SingleLicenseCheckStatus) {
    this.logger.debug("onLicenseCheckEvent: {}: {}", event.source, event.message)
    this.eventSubject.onNext(event.message)
  }

  private fun findExistingStrategy(
    targetURI: URI
  ): ManifestFulfillmentStrategyType {
    return if (this.isOverdrive()) {
      this.logger.debug("findExistingStrategy: trying an Overdrive strategy")

      val secretService =
        this.request.services.optionalService(
          AudioBookOverdriveSecretServiceType::class.java
        ) ?: throw UnsupportedOperationException("No Overdrive secret service is available")

      val strategies =
        this.request.strategyRegistry.findStrategy(
          OPAManifestFulfillmentStrategyProviderType::class.java
        ) ?: throw UnsupportedOperationException("No Overdrive fulfillment strategy is available")

      val parameters =
        when (val credentials = this.request.credentials) {
          null -> {
            throw UnsupportedOperationException(
              "Credentials are required for Overdrive fulfillment"
            )
          }

          is ManifestFulfillmentCredentialsBasic -> {
            val password = credentials.password
            val opaPassword = if (password.isBlank()) {
              OPAPassword.NotRequired
            } else {
              Password(password)
            }

            OPAParameters(
              userName = credentials.userName,
              password = opaPassword,
              clientKey = secretService.clientKey.orEmpty(),
              clientPass = secretService.clientPass.orEmpty(),
              targetURI = Indirect(targetURI),
              userAgent = this.request.userAgent
            )
          }

          is ManifestFulfillmentCredentialsToken -> {
            throw UnsupportedOperationException(
              "Can't use bearer tokens for Overdrive fulfillment"
            )
          }
        }

      strategies.create(parameters)
    } else {
      this.logger.debug("findExistingStrategy: trying a Basic strategy")

      val strategies =
        this.request.strategyRegistry.findStrategy(
          ManifestFulfillmentBasicType::class.java
        ) ?: throw UnsupportedOperationException("No Basic fulfillment strategy is available")

      val parameters =
        ManifestFulfillmentBasicParameters(
          uri = targetURI,
          credentials = this.request.credentials,
          httpClient = this.request.services.requireService(LSHTTPClientType::class.java),
          userAgent = this.request.userAgent
        )

      strategies.create(parameters)
    }
  }

  /**
   * @return `true` if the request content type implies an Overdrive audio book
   */

  private fun isOverdrive(): Boolean {
    return BookFormats.audioBookOverdriveMimeTypes()
      .map { it.fullType }
      .contains(this.request.contentType.fullType)
  }
}
