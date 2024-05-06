package org.nypl.simplified.books.audio

import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.nypl.simplified.taskrecorder.api.TaskResult

class AudioBookManifestFulfillmentError(
  val taskFailure: TaskResult.Failure<AudioBookManifestData>
) : ManifestFulfillmentErrorType {

  override val message: String
    get() = taskFailure.message

  override val serverData: ManifestFulfillmentErrorType.ServerData?
    get() = null
}
