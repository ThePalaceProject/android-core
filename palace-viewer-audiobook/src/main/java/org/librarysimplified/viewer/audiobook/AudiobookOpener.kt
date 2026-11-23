package org.librarysimplified.viewer.audiobook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import org.librarysimplified.audiobook.api.PlayerBookCredentialsNone
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
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.viewer.spi.ViewerParameters
import org.slf4j.LoggerFactory
import org.thepalaceproject.audiobook.now_playing.api.AudiobookNowPlayingServiceType
import org.thepalaceproject.audiobook.now_playing.api.AudiobookRef
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture

/**
 * Opens an audiobook for playback, without requiring an [Activity].
 *
 * This is the activity-free core of the open pipeline. It takes any [Context] (an
 * [Application] is fine), prepares the manifest, and opens the player. Launching the
 * player screen is optional, controlled by [launchUI].
 *
 * If the book is already open, it attaches instead of re-opening: when [launchUI] is set it
 * shows the player screen, otherwise it does nothing (the book is already playing).
 */

object AudiobookOpener {
  private val logger =
    LoggerFactory.getLogger(AudiobookOpener::class.java)

  fun open(
    context: Context,
    parameters: ViewerParameters,
    book: Book,
    format: BookFormat,
    launchUI: Boolean,
  ): CompletableFuture<Unit> {
    /*
     * Attach guard: if this book is already open, attach instead of re-opening. Re-running
     * the pipeline would call PlayerModel.openPlayerForManifest, which closes the current
     * book before opening the new one, so it would stop and restart the same playback.
     */

    val palaceID =
      PlayerPalaceID(book.entry.id)
    val currentState =
      PlayerModel.state
    if (currentState is PlayerModelState.PlayerOpen && currentState.palaceId == palaceID) {
      this.logger.debug("Book {} is already open; attaching.", palaceID)
      if (launchUI) {
        this.launchPlayerActivity(context)
      }
      return CompletableFuture.completedFuture(Unit)
    }

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

    val application =
      context.applicationContext as Application
    val cacheDir =
      context.cacheDir

    val profileCurrent =
      profiles.profileCurrent()
    val formatAudio =
      format as BookFormat.BookFormatAudioBook
    val file =
      formatAudio.file
    val manifest =
      formatAudio.manifest

    val preferences = profileCurrent.preferences()
    PlayerModel.setSeekIncrementForwardMs(preferences.audioBookPlaybackSkipIntervalForwardMs)
    PlayerModel.setSeekIncrementBackwardMs(preferences.audioBookPlaybackSkipIntervalBackwardMs)

    PlayerModel.bookAuthor = book.entry.authorsCommaSeparated
    PlayerModel.bookTitle = book.entry.title

    val account =
      profileCurrent.account(book.account)

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
        httpClient = httpClient,
      )

    this.loadAndConfigureFeedbooks(services)

    val bookCredentials =
      formatAudio.drmInformation.playerCredentials()

    /*
     * We now have to deal with backwards compatibility:
     */

    val drmInformation =
      format.drmInformation

    AudioBookViewerModel.parameters =
      AudioBookPlayerParameters(
        accountID = book.account,
        accountProviderID = account.provider.id,
        bookID = book.id,
        drmInfo = drmInformation,
        opdsEntry = book.entry,
        playerID = parameters.viewerID,
      )

    /*
     * Handle failure injection. The debug menu can be configured to make audiobooks fail
     * in order to test that we report errors properly.
     */

    val failInjection =
      parameters.flags["Fail"]
    if (failInjection != null && failInjection) {
      this.triggerFailure(
        context = context,
        httpClient = httpClient,
        palaceID = palaceID,
        launchUI = launchUI,
      )
      val failure = CompletableFuture<Unit>()
      failure.completeExceptionally(IllegalStateException("failure injection"))
      return failure
    }

    /*
     * We might only have a book file if the user is coming from a previous version of the app
     * that used "packaged" audiobook files. If this is the case, then the packaged audiobook
     * needs to be passed in to the audiobook library.
     */

    if (file != null) {
      val prepFuture =
        PlayerModel.downloadLocalPackagedAudiobook(
          bookCredentials = bookCredentials,
          bookFile = file,
          cacheDir = cacheDir,
          context = application,
          httpClient = httpClient,
          licenseChecks = licenseChecks,
          palaceID = palaceID,
          parserExtensions = parserExtensions,
        )
      if (launchUI) {
        this.launchPlayerActivity(context)
      }
      return this.openPlayerWhenManifestIsReady(
        application = application,
        httpClient = httpClient,
        book = book,
        prepFuture = prepFuture,
      )
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
        val prepFuture =
          PlayerModel.parseAndCheckLCPLicense(
            bookCredentials = bookCredentials,
            cacheDir = cacheDir,
            httpClient = httpClient,
            licenseBytes = licenseBytes,
            licenseChecks = licenseChecks,
            manifestUnparsed = ManifestUnparsed(palaceID, manifest.manifestFile.readBytes()),
            parserExtensions = parserExtensions,
          )
        if (launchUI) {
          this.launchPlayerActivity(context)
        }
        return this.openPlayerWhenManifestIsReady(
          application = application,
          httpClient = httpClient,
          book = book,
          prepFuture = prepFuture,
        )
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
          cacheDirectory = cacheDir,
          contentType = format.contentType,
          authorizationHandler = AudioBookViewerModel.authorizationHandler,
          httpClient = httpClient,
          palaceID = palaceID,
          services = services,
          target = AudioBookLink.Manifest(manifestURI),
        )

      val result =
        CompletableFuture<Unit>()

      val downloadFuture =
        PlayerModel.downloadParseAndCheckManifest(
          bookCredentials = bookCredentials,
          cacheDir = cacheDir,
          httpClient = httpClient,
          licenseChecks = licenseChecks,
          palaceID = palaceID,
          parserExtensions = parserExtensions,
          sourceURI = manifestURI,
          strategy =
            strategies
              .createStrategy(
                context = application,
                request = manifestRequest,
              ).toManifestStrategy(),
        )

      if (launchUI) {
        this.launchPlayerActivity(context)
      }

      downloadFuture.whenComplete { _, _ ->
        val state =
          PlayerModel.state

        /*
         * If the download doesn't fail, then the manifest is ready.
         */

        if (state !is PlayerModelState.PlayerManifestDownloadFailed) {
          this
            .openPlayerWhenManifestIsReady(
              application = application,
              httpClient = httpClient,
              book = book,
              prepFuture = downloadFuture,
            ).whenComplete { _, error ->
              this.completeResult(result, error)
            }
          return@whenComplete
        }

        /*
         * If the download fails, then try just parsing what we have locally.
         */

        this.logger.debug("Download failed; parsing local manifest.")
        val parseFuture =
          PlayerModel.parseAndCheckManifest(
            bookCredentials = bookCredentials,
            cacheDir = cacheDir,
            licenseChecks = licenseChecks,
            httpClient = httpClient,
            manifest =
              ManifestFulfilled(
                source = null,
                contentType = format.contentType,
                authorization = null,
                data = manifest.manifestFile.readBytes(),
              ),
            palaceID = palaceID,
            parserExtensions = parserExtensions,
          )

        this
          .openPlayerWhenManifestIsReady(
            application = application,
            httpClient = httpClient,
            book = book,
            prepFuture = parseFuture,
          ).whenComplete { _, error ->
            this.completeResult(result, error)
          }
      }
      return result
    }

    this.logger.debug("Parsing existing manifest.")
    val prepFuture =
      PlayerModel.parseAndCheckManifest(
        bookCredentials = bookCredentials,
        cacheDir = cacheDir,
        licenseChecks = licenseChecks,
        httpClient = httpClient,
        manifest =
          ManifestFulfilled(
            source = null,
            contentType = format.contentType,
            authorization = null,
            data = manifest.manifestFile.readBytes(),
          ),
        palaceID = palaceID,
        parserExtensions = parserExtensions,
      )
    if (launchUI) {
      this.launchPlayerActivity(context)
    }
    return this.openPlayerWhenManifestIsReady(
      application = application,
      httpClient = httpClient,
      book = book,
      prepFuture = prepFuture,
    )
  }

  /**
   * Once the manifest preparation has completed and the manifest is in a usable state
   * ([PlayerModelState.PlayerManifestOK]), open the player. When the player has opened
   * ([PlayerModelState.PlayerOpen]) the book is recorded as the current audiobook.
   */

  private fun openPlayerWhenManifestIsReady(
    application: Application,
    httpClient: LSHTTPClientType,
    book: Book,
    prepFuture: CompletableFuture<Unit>,
  ): CompletableFuture<Unit> {
    val result =
      CompletableFuture<Unit>()
    prepFuture.whenComplete { _, prepError ->
      val state =
        PlayerModel.state
      if (state !is PlayerModelState.PlayerManifestOK) {
        if (prepError != null) {
          result.completeExceptionally(prepError)
        } else {
          result.completeExceptionally(
            IllegalStateException("Manifest did not reach PlayerManifestOK"),
          )
        }
        return@whenComplete
      }

      val parameters =
        AudioBookViewerModel.parameters
      if (parameters == null) {
        result.completeExceptionally(IllegalStateException("No player parameters"))
        return@whenComplete
      }

      this.logger.debug("Manifest is ready; opening the player.")
      val openFuture =
        PlayerModel.openPlayerForManifest(
          authorizationHandler = AudioBookViewerModel.authorizationHandler,
          bookCredentials = parameters.drmInfo.playerCredentials(),
          bookSource = state.bookSource,
          context = application,
          fetchAll = true,
          httpClient = httpClient,
          manifest = state.manifest,
          playerID = parameters.playerID,
        )

      openFuture.whenComplete { _, openError ->
        if (PlayerModel.state is PlayerModelState.PlayerOpen) {
          this.recordNowPlaying(book)
          result.complete(Unit)
        } else {
          if (openError != null) {
            result.completeExceptionally(openError)
          } else {
            result.completeExceptionally(IllegalStateException("Book did not open"))
          }
        }
      }
    }
    return result
  }

  private fun completeResult(
    result: CompletableFuture<Unit>,
    error: Throwable?,
  ) {
    if (error == null) {
      result.complete(Unit)
    } else {
      result.completeExceptionally(error)
    }
  }

  private fun recordNowPlaying(book: Book) {
    Services
      .serviceDirectory()
      .requireService(AudiobookNowPlayingServiceType::class.java)
      .setCurrent(
        AudiobookRef(
          accountID = book.account,
          bookID = book.id,
        ),
      )
  }

  private fun launchPlayerActivity(context: Context) {
    val intent =
      Intent(context, AudioBookPlayerActivity2::class.java)
    if (context !is Activity) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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

  private fun triggerFailure(
    context: Context,
    httpClient: LSHTTPClientType,
    palaceID: PlayerPalaceID,
    launchUI: Boolean,
  ) {
    PlayerModel.parseAndCheckManifest(
      bookCredentials = PlayerBookCredentialsNone,
      cacheDir = context.cacheDir,
      licenseChecks = listOf(),
      httpClient = httpClient,
      manifest =
        ManifestFulfilled(
          source = null,
          contentType = BookFormats.audioBookGenericMimeTypes().iterator().next(),
          authorization = null,
          data = ByteArray(0),
        ),
      palaceID = palaceID,
      parserExtensions = listOf(),
    )
    if (launchUI) {
      this.launchPlayerActivity(context)
    }
  }
}
