package org.nypl.simplified.tests.mocking

import android.app.Application
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.audio.AudioBookStrategyType

class MockAudioBookManifestStrategies : AudioBookManifestStrategiesType {

  var strategy = MockAudioBookStrategy()

  override fun createStrategy(
    context: Application,
    request: AudioBookManifestRequest
  ): AudioBookStrategyType {
    return this.strategy
  }
}
