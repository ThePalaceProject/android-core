package org.nypl.simplified.books.audio

import kotlinx.coroutines.runBlocking
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.readium.r2.shared.fetcher.ArchiveFetcher
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.use
import org.slf4j.LoggerFactory

/**
 * An audio book manifest strategy that extracts the manifest from a downloaded audio book file.
 */

class PackagedAudioBookManifestStrategy(
  private val request: AudioBookManifestRequest
) : AbstractAudioBookManifestStrategy(request) {

  private val logger =
    LoggerFactory.getLogger(PackagedAudioBookManifestStrategy::class.java)

  override fun fulfill(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    taskRecorder.beginNewStep("Extracting manifest…")

    return this.extractManifest()
  }

  /**
   * Attempt to synchronously extract a manifest file from the audio book package.
   */

  private fun extractManifest(): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    if (this.request.file == null) {
      return PlayerResult.Failure(ExtractFailed("No audio book file"))
    }

    val manifestURI = this.request.targetURI
    val manifestLink = Link(manifestURI.toString())
    val filePath = this.request.file.absolutePath

    this.logger.debug("extractManifest: extracting {} from {}", manifestURI, filePath)

    val manifestBytes = runBlocking {
      ArchiveFetcher.fromPath(filePath)?.use { archiveFetcher ->
        archiveFetcher.get(manifestLink).read().getOrNull()
      }
    }

    return if (manifestBytes == null) {
      PlayerResult.Failure(ExtractFailed("Unable to extract manifest from audio book file"))
    } else {
      PlayerResult.unit(ManifestFulfilled(this.request.contentType, manifestBytes))
    }
  }

  private data class ExtractFailed(
    override val message: String,
    val exception: java.lang.Exception? = null,
    override val serverData: ManifestFulfillmentErrorType.ServerData? = null
  ) : ManifestFulfillmentErrorType
}
