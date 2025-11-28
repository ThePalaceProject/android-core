package org.nypl.simplified.books.audio

import io.reactivex.Observable
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.nypl.simplified.taskrecorder.api.TaskResult

/**
 * A strategy for obtaining, parsing, and license-checking an audio book manifest. A given
 * strategy may be executed any number of times and should (in the absence of errors) return
 * the same result each time it is executed.
 */

interface AudioBookStrategyType {

  /**
   * An observable source of events published during fulfillment.
   */

  val events: Observable<String>

  /**
   * Execute the strategy.
   */

  fun execute(): TaskResult<AudioBookManifestData>

  /**
   * Adapt this strategy to a manifest fulfillment strategy.
   */

  fun toManifestStrategy(): ManifestFulfillmentStrategyType
}
