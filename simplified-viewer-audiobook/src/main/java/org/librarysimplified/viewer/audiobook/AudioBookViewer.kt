package org.librarysimplified.viewer.audiobook

import android.app.Activity
import android.content.Intent
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.feedbooks.FeedbooksPlayerExtension
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.audio.AudioBookFeedbooksSecretServiceType
import org.nypl.simplified.books.audio.AudioBookManifestFulfillmentAdapter
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.networkconnectivity.api.NetworkConnectivityType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.ServiceLoader

/**
 * An audio book viewer service.
 */

class AudioBookViewer : ViewerProviderType {

  private val logger =
    LoggerFactory.getLogger(AudioBookViewer::class.java)

  override val name: String =
    "org.librarysimplified.viewer.audiobook.AudioBookViewer"

  override fun canSupport(
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat
  ): Boolean {
    return when (format) {
      is BookFormat.BookFormatEPUB,
      is BookFormat.BookFormatPDF -> {
        this.logger.debug("audio book viewer can only view audio books")
        false
      }

      is BookFormat.BookFormatAudioBook ->
        true
    }
  }

  override fun canPotentiallySupportType(type: MIMEType): Boolean {
    return StandardFormatNames.allAudioBooks.contains(type)
  }

  private fun loadAndConfigureFeedbooks(services: ServiceDirectoryType) {
    val feedbooksConfigService =
      services.optionalService(AudioBookFeedbooksSecretServiceType::class.java)

    if (feedbooksConfigService != null) {
      this.logger.debug("Feedbooks configuration service is available; configuring extension")
      val extension =
        PlayerModel.playerExtensions
          .filterIsInstance<FeedbooksPlayerExtension>()
          .firstOrNull()
      if (extension != null) {
        this.logger.debug("Feedbooks extension is available")
        extension.configuration = feedbooksConfigService.configuration
      } else {
        this.logger.debug("Feedbooks extension is not available")
      }
    }
  }

  override fun open(
    activity: Activity,
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat,
    accountProviderId: URI
  ) {
    val services =
      Services.serviceDirectory()
    val httpClient =
      services.requireService(LSHTTPClientType::class.java)
    val networkConnectivity =
      services.requireService(NetworkConnectivityType::class.java)
    val strategies =
      services.requireService(AudioBookManifestStrategiesType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val parserExtensions =
      ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
    val licenseChecks =
      ServiceLoader.load(SingleLicenseCheckProviderType::class.java).toList()

    val formatAudio =
      format as BookFormat.BookFormatAudioBook
    val file =
      formatAudio.file
    val manifest =
      formatAudio.manifest

    PlayerModel.bookAuthor = book.entry.authorsCommaSeparated
    PlayerModel.bookTitle = book.entry.title

    this.loadAndConfigureFeedbooks(services)

    if (manifest != null) {
      val parameters =
        AudioBookPlayerParameters(
          accountID = book.account,
          accountProviderID = accountProviderId,
          bookID = book.id,
          file = file,
          drmInfo = formatAudio.drmInformation,
          manifestContentType = format.contentType.fullType,
          manifestFile = manifest.manifestFile,
          manifestURI = manifest.manifestURI,
          opdsEntry = book.entry,
          userAgent = httpClient.userAgent()
        )

      AudioBookViewerModel.parameters = parameters

      val accountCredentials =
        profiles.profileCurrent()
          .account(book.account)
          .loginState
          .credentials

      val strategy =
        parameters.toManifestStrategy(
          application = activity.application,
          strategies = strategies,
          isNetworkAvailable = { networkConnectivity.isNetworkAvailable },
          credentials = accountCredentials,
          cacheDirectory = activity.cacheDir
        )

      val strategyAdapted =
        AudioBookManifestFulfillmentAdapter(strategy)

      PlayerModel.downloadParseAndCheckManifest(
        sourceURI = manifest.manifestURI,
        userAgent = PlayerUserAgent(httpClient.userAgent()),
        cacheDir = activity.cacheDir,
        licenseChecks = licenseChecks,
        parserExtensions = parserExtensions,
        strategy = strategyAdapted,
        bookCredentials = formatAudio.drmInformation.playerCredentials(),
        palaceID = PlayerPalaceID(book.entry.id)
      )
    } else {
      AudioBookViewerModel.parameters = null
    }

    activity.startActivity(Intent(activity, AudioBookPlayerActivity2::class.java))
  }
}
