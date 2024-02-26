package org.nypl.simplified.tests.mocking

import android.app.Application
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.audio.AudioBookManifestStrategyType

class MockAudioBookManifestStrategies : AudioBookManifestStrategiesType {

  var strategy = MockAudioBookManifestStrategy()

  override fun createStrategy(
    context: Application,
    request: AudioBookManifestRequest
  ): AudioBookManifestStrategyType {
    return this.strategy
  }
}
