package org.nypl.simplified.books.audio

import io.reactivex.Observable
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.nypl.simplified.taskrecorder.api.TaskResult

class AudioBookManifestFulfillmentAdapter(
  val strategy: AudioBookManifestStrategyType
) : ManifestFulfillmentStrategyType {

  private var resultField: TaskResult<AudioBookManifestData> =
    TaskResult.fail(
      "Not yet executed.",
      "Not yet executed.",
      "error-not-executed"
    )

  val result: TaskResult<AudioBookManifestData>
    get() = this.resultField

  override val events: Observable<ManifestFulfillmentEvent>
    get() = this.strategy.events.map(::ManifestFulfillmentEvent)

  override fun close() {
    // Nothing to close
  }

  override fun execute(): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    this.resultField = this.strategy.execute()

    return when (val result = this.strategy.execute()) {
      is TaskResult.Failure -> {
        PlayerResult.Failure(
          AudioBookManifestFulfillmentError(result)
        )
      }

      is TaskResult.Success -> {
        PlayerResult.Success(result.result.fulfilled)
      }
    }
  }
}
