package org.nypl.simplified.bookmarks.internal

import org.slf4j.LoggerFactory

/**
 * A trivial Thread subclass for efficient checks to determine whether or not the current
 * thread is a service thread.
 */

internal class BServiceThread(thread: Thread) : Thread(thread) {

  companion object {
    private val logger =
      LoggerFactory.getLogger(BServiceThread::class.java)

    fun checkServiceThread() {
      val currentThread = currentThread()
      if (currentThread is BServiceThread) {
        return
      }

      this.logger.debug("bookmark service used wrong thread")
      throw IllegalStateException("Current thread is not the service thread")
    }
  }
}
