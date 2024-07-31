package org.librarysimplified.viewer.audiobook

import android.app.Activity
import android.content.Intent
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.feedbooks.FeedbooksPlayerExtension
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.audio.AudioBookFeedbooksSecretServiceType
import org.nypl.simplified.books.audio.AudioBookLink
import org.nypl.simplified.books.audio.AudioBookManifestRequest
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

    PlayerModel.setStreamingPermitted(true)
    PlayerModel.bookAuthor = book.entry.authorsCommaSeparated
    PlayerModel.bookTitle = book.entry.title

    val account =
      profiles.profileCurrent()
        .account(book.account)
    val accountCredentials =
      account.loginState
        .credentials

    this.loadAndConfigureFeedbooks(services)

    val userAgent =
      PlayerUserAgent(httpClient.userAgent())
    val bookCredentials =
      formatAudio.drmInformation.playerCredentials()

    /*
     * We now have to deal with backwards compatibility:
     */

    val drmInformation =
      format.drmInformation

    AudioBookViewerModel.parameters =
      AudioBookPlayerParameters(
        userAgent = userAgent.userAgent,
        accountID = book.account,
        bookID = book.id,
        opdsEntry = book.entry,
        accountProviderID = account.provider.id,
        drmInfo = drmInformation
      )

    /*
     * We might only have a book file if the user is coming from a previous version of the app
     * that used "packaged" audiobook files. If this is the case, then the packaged audiobook
     * needs to be passed in to the audiobook library.
     */

    if (file != null) {
      PlayerModel.downloadLocalPackagedAudiobook(
        bookCredentials = bookCredentials,
        bookFile = file,
        cacheDir = activity.cacheDir,
        context = activity.application,
        licenseChecks = licenseChecks,
        parserExtensions = parserExtensions,
        userAgent = userAgent,
      )
      this.openActivity(activity)
      return
    }

    /*
     * If we don't have a book file, then the user might have just checked out a book using
     * the current version of the app. Therefore, we might have a license file. If we do, then
     * the book needs to be opened using the license. If we have a license file, this implies
     * that we also have a manifest, because the version of the app that started saving license
     * files also started saving manifests.
     */

    if (drmInformation is BookDRMInformation.LCP) {
      val licenseBytes = drmInformation.licenseBytes
      if (licenseBytes != null && manifest != null) {
        PlayerModel.parseAndCheckLCPLicense(
          bookCredentials = bookCredentials,
          licenseBytes = licenseBytes,
          manifestBytes = manifest.manifestFile.readBytes(),
          cacheDir = activity.cacheDir,
          licenseChecks = licenseChecks,
          parserExtensions = parserExtensions,
          userAgent = userAgent,
        )
        this.openActivity(activity)
        return
      }
    }

    /*
     * Otherwise, we must only have a manifest file, but we might also have a manifest source URI.
     * If we have a manifest source URI, then a new version of the manifest should be downloaded.
     */

    check(manifest != null) { "Manifest must be present" }

    val manifestURI = manifest.manifestURI
    if (manifestURI != null && manifestURI.isAbsolute) {
      val manifestRequest =
        AudioBookManifestRequest(
          httpClient = httpClient,
          target = AudioBookLink.Manifest(manifestURI),
          contentType = format.contentType,
          userAgent = userAgent,
          cacheDirectory = activity.cacheDir,
          credentials = accountCredentials,
          services = services
        )

      PlayerModel.downloadParseAndCheckManifest(
        sourceURI = manifestURI,
        userAgent = userAgent,
        cacheDir = activity.cacheDir,
        licenseChecks = licenseChecks,
        parserExtensions = parserExtensions,
        strategy = strategies.createStrategy(
          context = activity.application,
          request = manifestRequest
        ).toManifestStrategy(),
        bookCredentials = bookCredentials
      )
      this.openActivity(activity)
      return
    }

    PlayerModel.parseAndCheckManifest(
      cacheDir = activity.cacheDir,
      manifest = ManifestFulfilled(
        source = null,
        contentType = format.contentType,
        authorization = null,
        data = manifest.manifestFile.readBytes()
      ),
      licenseChecks = licenseChecks,
      userAgent = userAgent,
      parserExtensions = parserExtensions,
      bookCredentials = bookCredentials
    )
    this.openActivity(activity)
  }

  private fun openActivity(activity: Activity) {
    activity.startActivity(Intent(activity, AudioBookPlayerActivity2::class.java))
  }
}
