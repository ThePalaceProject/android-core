package org.nypl.simplified.books.audio

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.asset.ResourceAsset
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.slf4j.LoggerFactory
import java.io.File

/**
 * An audio book manifest strategy that extracts the manifest from a downloaded audio book file.
 */

class PackagedAudioBookManifestStrategy(
  private val context: Application,
  private val request: AudioBookManifestRequest
) : AbstractAudioBookManifestStrategy(context, request) {

  private val logger =
    LoggerFactory.getLogger(PackagedAudioBookManifestStrategy::class.java)

  override fun fulfill(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    taskRecorder.beginNewStep("Extracting manifest…")
    return this.extractManifest(taskRecorder)
  }

  /**
   * Attempt to synchronously extract a manifest file from the audio book package.
   */

  private fun extractManifest(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    if (this.request.file == null) {
      taskRecorder.currentStepFailed("No audio book file", "no-audio-book-file")
      return PlayerResult.Failure(ExtractFailed("No audio book file"))
    }

    val manifestURI = this.request.targetURI
    val manifestLink = Url(manifestURI.toString())!!
    val filePath = this.request.file.absolutePath

    this.logger.debug("extractManifest: extracting {} from {}", manifestURI, filePath)

    val assetRetriever =
      AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = DefaultHttpClient()
      )

    val manifestBytes = runBlocking {
      return@runBlocking when (val r = assetRetriever.retrieve(File(filePath))) {
        is Try.Failure -> {
          taskRecorder.currentStepFailed(
            message = "Failed to open file $filePath.",
            errorCode = "audio-book-file"
          )
          null
        }

        is Try.Success -> {
          when (val c = r.value) {
            is ContainerAsset -> {
              when (val d = c.container[manifestLink]?.read()) {
                is Try.Failure -> {
                  taskRecorder.currentStepFailed(
                    message = "Failed to read $manifestLink from container",
                    errorCode = "audio-book-no-manifest"
                  )
                  null
                }

                is Try.Success -> {
                  d.value
                }

                null -> {
                  taskRecorder.currentStepFailed(
                    message = "Asset retriever returned null for $filePath",
                    errorCode = "audio-book-asset-retriever-null-result"
                  )
                  null
                }
              }
            }

            is ResourceAsset -> {
              taskRecorder.currentStepFailed(
                message = "Returned asset $filePath is not a container asset",
                errorCode = "audio-book-not-container"
              )
              null
            }
          }
        }
      }
    }

    return if (manifestBytes == null) {
      PlayerResult.Failure(ExtractFailed("Unable to extract manifest from audio book file"))
    } else {
      PlayerResult.unit(ManifestFulfilled(this.request.contentType, null, manifestBytes))
    }
  }

  private data class ExtractFailed(
    override val message: String,
    val exception: java.lang.Exception? = null,
    override val serverData: ManifestFulfillmentErrorType.ServerData? = null
  ) : ManifestFulfillmentErrorType
}
