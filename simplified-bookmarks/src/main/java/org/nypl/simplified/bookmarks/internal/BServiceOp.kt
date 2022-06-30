package org.nypl.simplified.bookmarks.internal

import org.slf4j.Logger
import java.util.concurrent.Callable

/**
 * The type of operations that can be performed in the controller.
 */

internal abstract class BServiceOp<T>(
  val logger: Logger
) : Callable<T> {

  abstract fun runActual(): T

  override fun call(): T {
    try {
      this.logger.debug("{}: started", this.javaClass.simpleName)
      BServiceThread.checkServiceThread()
      return this.runActual()
    } finally {
      this.logger.debug("{}: finished", this.javaClass.simpleName)
    }
  }
}
