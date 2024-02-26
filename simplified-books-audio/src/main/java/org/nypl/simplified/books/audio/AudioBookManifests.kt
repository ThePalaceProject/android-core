package org.nypl.simplified.books.audio

import android.app.Application

/**
 * The default provider of manifest strategies.
 */

object AudioBookManifests : AudioBookManifestStrategiesType {
  override fun createStrategy(
    context: Application,
    request: AudioBookManifestRequest
  ): AudioBookManifestStrategyType {
    return if (request.file != null) {
      PackagedAudioBookManifestStrategy(context, request)
    } else {
      UnpackagedAudioBookManifestStrategy(context, request)
    }
  }
}
