package org.nypl.simplified.tests.mocking

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.nypl.simplified.books.audio.AudioBookManifestData
import org.nypl.simplified.books.audio.AudioBookStrategyType
import org.nypl.simplified.taskrecorder.api.TaskResult

class MockAudioBookStrategy : AudioBookStrategyType {

  var onExecute: () -> TaskResult<AudioBookManifestData> = {
    TaskResult.fail("Failed", "Failed", "failed")
  }

  var eventSubject: PublishSubject<String> =
    PublishSubject.create()

  override val events: Observable<String> =
    this.eventSubject

  override fun execute(): TaskResult<AudioBookManifestData> {
    return this.onExecute.invoke()
  }

  override fun toManifestStrategy(): ManifestFulfillmentStrategyType {
    TODO("Not yet implemented")
  }
}
