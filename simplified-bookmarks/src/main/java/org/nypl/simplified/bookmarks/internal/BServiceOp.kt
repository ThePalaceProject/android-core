package org.nypl.simplified.bookmarks.internal

import org.slf4j.Logger
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.Callable

/**
 * The type of operations that can be performed in the controller.
 */

internal abstract class BServiceOp<T>(
  val logger: Logger
) : Callable<T> {

  abstract fun runActual(): T

  override fun call(): T {
    val timeThen = OffsetDateTime.now()
    try {
      this.logger.debug("{}: Started bookmark task", this.javaClass.simpleName)
      BServiceThread.checkServiceThread()
      return this.runActual()
    } finally {
      val timeNow = OffsetDateTime.now()
      this.logger.debug(
        "{}: Finished bookmark task in {}",
        this.javaClass.simpleName,
        Duration.between(timeThen, timeNow)
      )
    }
  }
}
