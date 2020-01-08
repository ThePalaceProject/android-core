package org.nypl.simplified.viewer.audiobook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.io7m.jfunctional.Option
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import org.librarysimplified.audiobook.api.PlayerManifest
import org.librarysimplified.audiobook.api.PlayerManifests
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.downloader.core.DownloadListenerType
import org.nypl.simplified.downloader.core.DownloadType
import org.nypl.simplified.files.FileUtilities
import org.nypl.simplified.http.core.HTTPProblemReport
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI

/**
 * A fragment that downloads and updates an audio book manifest.
 */

class AudioBookLoadingFragment : Fragment() {

  companion object {

    const val parametersKey = "org.nypl.simplified.viewer.audiobook.AudioBookLoadingFragment.parameters"

    /**
     * Create a new fragment.
     */

    fun newInstance(parameters: AudioBookLoadingFragmentParameters): AudioBookLoadingFragment {
      val args = Bundle()
      args.putSerializable(parametersKey, parameters)
      val fragment = AudioBookLoadingFragment()
      fragment.arguments = args
      return fragment
    }
  }

  private lateinit var listener: AudioBookLoadingFragmentListenerType
  private lateinit var loadingParameters: AudioBookLoadingFragmentParameters
  private lateinit var playerParameters: AudioBookPlayerParameters
  private lateinit var profiles: ProfilesControllerType
  private lateinit var progress: ProgressBar
  private lateinit var uiThread: UIThreadServiceType
  private val log = LoggerFactory.getLogger(AudioBookLoadingFragment::class.java)
  private var download: DownloadType? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?
  ): View? {
    return inflater.inflate(R.layout.audio_book_player_loading, container, false)
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    super.onViewCreated(view, state)

    this.progress = view.findViewById(R.id.audio_book_loading_progress)
    this.progress.isIndeterminate = true
    this.progress.max = 100
  }

  override fun onCreate(state: Bundle?) {
    this.log.debug("onCreate")

    super.onCreate(state)

    this.loadingParameters =
      this.arguments!!.getSerializable(parametersKey)
        as AudioBookLoadingFragmentParameters

    val services = Services.serviceDirectory()

    this.profiles =
      services.requireService(ProfilesControllerType::class.java)
    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)
  }

  override fun onActivityCreated(state: Bundle?) {
    super.onActivityCreated(state)

    this.listener = this.activity as AudioBookLoadingFragmentListenerType
    this.playerParameters = this.listener.onLoadingFragmentWantsAudioBookParameters()

    /*
     * If network connectivity is available, download a new version of the manifest. If it isn't
     * available, just use the existing one.
     */

    val fragment = this
    if (this.listener.onLoadingFragmentIsNetworkConnectivityAvailable()) {
      val credentials =
        this.profiles.profileAccountForBook(this.playerParameters.bookID)
          .loginState
          .credentials

      if (credentials != null) {
        fragment.tryFetchNewManifest(
          credentials,
          fragment.playerParameters.manifestURI,
          fragment.listener)
      } else {
        fragment.listener.onLoadingFragmentLoadingFinished(
          this@AudioBookLoadingFragment.parseManifest(fragment.playerParameters.manifestFile))
      }
    } else {
      this.parseAndFinishManifest()
    }
  }

  private fun tryFetchNewManifest(
    credentials: AccountAuthenticationCredentials,
    manifestURI: URI,
    listener: AudioBookLoadingFragmentListenerType
  ) {

    val downloader = listener.onLoadingFragmentWantsDownloader()
    val fragment = this

    this.download =
      downloader.download(
        manifestURI,
        Option.some(AccountAuthenticatedHTTP.createAuthenticatedHTTP(credentials)),
        object : DownloadListenerType {
          override fun onDownloadStarted(
            download: DownloadType,
            expectedTotal: Long
          ) {
            fragment.onManifestDownloadStarted()
          }

          override fun onDownloadDataReceived(
            download: DownloadType,
            runningTotal: Long,
            expectedTotal: Long
          ) {
            fragment.onManifestDownloadDataReceived(
              runningTotal, expectedTotal)
          }

          override fun onDownloadCancelled(download: DownloadType) {
          }

          override fun onDownloadFailed(
            download: DownloadType,
            status: Int,
            runningTotal: Long,
            problemReport: OptionType<HTTPProblemReport>,
            exception: OptionType<Throwable>
          ) {
            fragment.onManifestDownloadFailed(status, exception)
          }

          override fun onDownloadCompleted(
            download: DownloadType,
            file: File
          ) {
            fragment.onManifestDownloaded(download.contentType, file)
          }
        })
  }

  private fun onManifestDownloaded(
    contentType: String,
    file: File
  ) {

    this.uiThread.runOnUIThread {
      this.progress.isIndeterminate = false
      this.progress.progress = 100
    }

    /*
     * Update the manifest in the book database.
     */

    val handle =
      this.profiles.profileAccountForBook(this.playerParameters.bookID)
        .bookDatabase
        .entry(this.playerParameters.bookID)
        .findFormatHandle(BookDatabaseEntryFormatHandleAudioBook::class.java)

    if (handle != null) {
      if (handle.formatDefinition.supportedContentTypes().contains(contentType)) {
        handle.copyInManifestAndURI(file, this.playerParameters.manifestURI)
        FileUtilities.fileDelete(file)
      } else {
        this.log.error(
          "Server delivered an unsupported content type: {}: ", contentType, IOException())
      }
    } else {
      this.log.error(
        "Bug: Book database entry has no audio book format handle", IllegalStateException())
    }

    this.parseAndFinishManifest()
  }

  private fun parseManifest(manifestFile: File): PlayerManifest {
    return FileInputStream(manifestFile).use { stream ->
      val parseResult = PlayerManifests.parse(stream)
      when (parseResult) {
        is PlayerResult.Success -> {
          parseResult.result
        }
        is PlayerResult.Failure -> {
          throw parseResult.failure
        }
      }
    }
  }

  private fun onManifestDownloadFailed(
    status: Int,
    exception: OptionType<Throwable>
  ) {

    if (exception is Some<Throwable>) {
      this.log.error("manifest download failed: status {}: ", status, exception.get())
    } else {
      this.log.error("manifest download failed: status {}", status)
    }

    this.parseAndFinishManifest()
  }

  private fun parseAndFinishManifest() {
    try {
      val manifest = this.parseManifest(this.playerParameters.manifestFile)
      this.listener.onLoadingFragmentLoadingFinished(manifest)
    } catch (ex: Exception) {
      this.listener.onLoadingFragmentLoadingFailed(ex)
    }
  }

  private fun onManifestDownloadDataReceived(
    runningTotal: Long,
    expectedTotal: Long
  ) {

    val progress = (runningTotal.toDouble() / expectedTotal.toDouble()) * 100.0
    this.uiThread.runOnUIThread {
      this.progress.isIndeterminate = false
      this.progress.progress = progress.toInt()
    }
  }

  private fun onManifestDownloadStarted() {
    this.uiThread.runOnUIThread {
      this.progress.progress = 0
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    this.log.debug("onDestroy")
    this.download?.cancel()
  }
}