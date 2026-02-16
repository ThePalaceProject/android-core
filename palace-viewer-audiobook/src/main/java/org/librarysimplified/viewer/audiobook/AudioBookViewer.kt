package org.librarysimplified.viewer.audiobook

import android.app.Activity
import android.content.Intent
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerBookCredentialsNone
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.feedbooks.FeedbooksPlayerExtension
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerModelState
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
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.viewer.spi.ViewerParameters
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An audio book viewer service.
 */

class AudioBookViewer : ViewerProviderType {

  private val logger =
    LoggerFactory.getLogger(AudioBookViewer::class.java)

  override val name: String =
    "org.librarysimplified.viewer.audiobook.AudioBookViewer"

  override fun canSupport(
    preferences: ViewerParameters,
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
        PlayerModel.authorizationHandlerExtensions
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
    parameters: ViewerParameters,
    book: Book,
    format: BookFormat,
    accountProviderId: URI
  ) {
    val services =
      Services.serviceDirectory()
    val httpClient =
      services.requireService(LSHTTPClientType::class.java)
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

    val palaceID =
      PlayerPalaceID(book.entry.id)
    val account =
      profiles.profileCurrent()
        .account(book.account)

    /*
     * Configure the login and authorization handlers.
     */

    AudioBookViewerModel.loginHandler = {
      parameters.onLoginRequested.invoke(book.account)
    }
    AudioBookViewerModel.authorizationHandler =
      AudioBookAuthorizationHandler(
        account = account,
        book = book,
        httpClient = httpClient
      )

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
     * Handle failure injection. The debug menu can be configured to make audiobooks fail
     * in order to test that we report errors properly.
     */

    if (parameters.flags["Fail"] ?: false) {
      this.triggerFailure(
        activity = activity,
        userAgent = userAgent,
        palaceID = palaceID,
      )
      return
    }

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
        palaceID = palaceID,
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
          cacheDir = activity.cacheDir,
          licenseBytes = licenseBytes,
          licenseChecks = licenseChecks,
          manifestUnparsed = ManifestUnparsed(palaceID, manifest.manifestFile.readBytes()),
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
      this.logger.debug("Attempting to download fresh manifest.")

      val manifestRequest =
        AudioBookManifestRequest(
          cacheDirectory = activity.cacheDir,
          contentType = format.contentType,
          authorizationHandler = AudioBookViewerModel.authorizationHandler,
          httpClient = httpClient,
          palaceID = palaceID,
          services = services,
          target = AudioBookLink.Manifest(manifestURI),
          userAgent = userAgent,
        )

      /*
       * The following series of future compositions is slightly unpleasant because Android
       * doesn't expose all of the CompletableFuture methods until you get up to about API 34.
       * Thanks.
       */

      val downloadFuture =
        PlayerModel.downloadParseAndCheckManifest(
          sourceURI = manifestURI,
          bookCredentials = bookCredentials,
          cacheDir = activity.cacheDir,
          licenseChecks = licenseChecks,
          palaceID = palaceID,
          parserExtensions = parserExtensions,
          strategy = strategies.createStrategy(
            context = activity.application,
            request = manifestRequest
          ).toManifestStrategy(),
          userAgent = userAgent,
        )

      val openAttempted =
        AtomicBoolean(false)
      val openFuture =
        CompletableFuture<Unit>()

      openFuture.whenComplete { _, _ ->
        UIThread.runOnUIThread {
          if (openAttempted.compareAndSet(false, true)) {
            this.openActivity(activity)
          }
        }
      }

      downloadFuture.whenComplete { t, _ ->

        /*
         * If the download doesn't fail, then open the player.
         */

        if (PlayerModel.state !is PlayerModelState.PlayerManifestDownloadFailed) {
          this.logger.debug("Download completed, opening player...")
          openFuture.complete(Unit)
          return@whenComplete
        }

        /*
         * If the download fails, then try just parsing what we have locally. When parsing
         * completes (either successfully or not), open the player activity so that we can
         * either see a player or an error.
         */

        val parseFuture =
          PlayerModel.parseAndCheckManifest(
            bookCredentials = bookCredentials,
            cacheDir = activity.cacheDir,
            licenseChecks = licenseChecks,
            manifest = ManifestFulfilled(
              source = null,
              contentType = format.contentType,
              authorization = null,
              data = manifest.manifestFile.readBytes()
            ),
            palaceID = palaceID,
            parserExtensions = parserExtensions,
            userAgent = userAgent,
          )

        parseFuture.whenComplete { _, _ ->
          this.logger.debug("Parsing completed, opening player...")
          openFuture.complete(Unit)
        }
      }
      return
    }

    this.logger.debug("Parsing existing manifest.")
    PlayerModel.parseAndCheckManifest(
      bookCredentials = bookCredentials,
      cacheDir = activity.cacheDir,
      licenseChecks = licenseChecks,
      manifest = ManifestFulfilled(
        source = null,
        contentType = format.contentType,
        authorization = null,
        data = manifest.manifestFile.readBytes()
      ),
      palaceID = palaceID,
      parserExtensions = parserExtensions,
      userAgent = userAgent,
    )
    this.openActivity(activity)
  }

  private fun triggerFailure(
    activity: Activity,
    userAgent: PlayerUserAgent,
    palaceID: PlayerPalaceID
  ) {
    PlayerModel.parseAndCheckManifest(
      bookCredentials = PlayerBookCredentialsNone,
      cacheDir = activity.cacheDir,
      licenseChecks = listOf(),
      manifest = ManifestFulfilled(
        source = null,
        contentType = BookFormats.audioBookGenericMimeTypes().iterator().next(),
        authorization = null,
        data = ByteArray(0)
      ),
      palaceID = palaceID,
      parserExtensions = listOf(),
      userAgent = userAgent,
    )
    this.openActivity(activity)
  }

  private fun openActivity(activity: Activity) {
    activity.startActivity(Intent(activity, AudioBookPlayerActivity2::class.java))
  }
}
