package org.nypl.simplified.books.audio

import com.io7m.junreachable.UnimplementedCodeException
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicCredentials
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestFulfillmentStrategyProviderType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestURI
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAParameters
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory

/**
 * An audio book manifest strategy that downloads the manifest from a URI, or loads a fallback if
 * the network is unavailable.
 */

class UnpackagedAudioBookManifestStrategy(
  private val request: AudioBookManifestRequest
) : AbstractAudioBookManifestStrategy(request) {

  private val logger =
    LoggerFactory.getLogger(UnpackagedAudioBookManifestStrategy::class.java)

  override fun fulfill(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    return if (this.request.isNetworkAvailable()) {
      taskRecorder.beginNewStep("Downloading manifest…")
      this.downloadManifest()
    } else {
      taskRecorder.beginNewStep("Loading manifest…")
      this.loadFallbackManifest()
    }
  }

  private data class DataLoadFailed(
    override val message: String,
    val exception: java.lang.Exception? = null,
    override val serverData: ManifestFulfillmentErrorType.ServerData? = null
  ) : ManifestFulfillmentErrorType

  private fun loadFallbackManifest(): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    this.logger.debug("loadFallbackManifest")
    return try {
      val data = this.request.loadFallbackData()
      if (data == null) {
        PlayerResult.Failure(DataLoadFailed("No fallback manifest data is provided"))
      } else {
        PlayerResult.unit(data)
      }
    } catch (e: Exception) {
      this.logger.error("loadFallbackManifest: ", e)
      PlayerResult.Failure(DataLoadFailed(e.message ?: e.javaClass.name, e))
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

  /**
   * Attempt to synchronously download a manifest file. If the download fails, return the
   * error details.
   */

  private fun downloadManifest(): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    this.logger.debug("downloadManifest")

    val strategy: ManifestFulfillmentStrategyType =
      this.downloadStrategyForCredentials()
    val fulfillSubscription =
      strategy.events.subscribe(this::onManifestFulfillmentEvent)

    try {
      return strategy.execute()
    } finally {
      fulfillSubscription.unsubscribe()
    }
  }

  /**
   * Try to find an appropriate fulfillment strategy based on the audio book request.
   */

  private fun downloadStrategyForCredentials(): ManifestFulfillmentStrategyType {
    return if (this.isOverdrive()) {
      this.logger.debug("downloadStrategyForCredentials: trying an Overdrive strategy")

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
          is AudioBookCredentials.UsernamePassword ->
            OPAParameters(
              userName = credentials.userName,
              password = OPAPassword.Password(credentials.password),
              clientKey = secretService.clientKey.orEmpty(),
              clientPass = secretService.clientPass.orEmpty(),
              targetURI = OPAManifestURI.Indirect(this.request.targetURI),
              userAgent = this.request.userAgent
            )
          is AudioBookCredentials.UsernameOnly ->
            OPAParameters(
              userName = credentials.userName,
              password = OPAPassword.NotRequired,
              clientKey = secretService.clientKey.orEmpty(),
              clientPass = secretService.clientPass.orEmpty(),
              targetURI = OPAManifestURI.Indirect(this.request.targetURI),
              userAgent = this.request.userAgent
            )
          is AudioBookCredentials.BearerToken ->
            throw UnsupportedOperationException("Can't use bearer tokens for Overdrive fulfillment")
          null ->
            throw UnimplementedCodeException()
        }

      strategies.create(parameters)
    } else {
      this.logger.debug("downloadStrategyForCredentials: trying a Basic strategy")

      val strategies =
        this.request.strategyRegistry.findStrategy(
          ManifestFulfillmentBasicType::class.java
        ) ?: throw UnsupportedOperationException("No Basic fulfillment strategy is available")

      val parameters =
        ManifestFulfillmentBasicParameters(
          uri = this.request.targetURI,
          credentials = this.request.credentials?.let { credentials ->
            when (credentials) {
              is AudioBookCredentials.UsernamePassword ->
                ManifestFulfillmentBasicCredentials(
                  userName = credentials.userName,
                  password = credentials.password
                )
              is AudioBookCredentials.UsernameOnly ->
                ManifestFulfillmentBasicCredentials(
                  userName = credentials.userName,
                  password = ""
                )
              is AudioBookCredentials.BearerToken ->
                throw UnsupportedOperationException(
                  "Can't use bearer tokens for audio book fulfillment"
                )
            }
          },
          httpClient = this.request.services.requireService(LSHTTPClientType::class.java),
          userAgent = this.request.userAgent
        )

      strategies.create(parameters)
    }
  }

  private fun onManifestFulfillmentEvent(event: ManifestFulfillmentEvent) {
    this.logger.debug("onManifestFulfillmentEvent: {}", event.message)
    this.eventSubject.onNext(event.message)
  }
}
