package org.nypl.simplified.books.reader.bookmarks.internal

import org.slf4j.LoggerFactory

/**
 * A trivial Thread subclass for efficient checks to determine whether or not the current
 * thread is a service thread.
 */

internal class RBServiceThread(thread: Thread) : Thread(thread) {

  companion object {
    private val logger =
      LoggerFactory.getLogger(RBServiceThread::class.java)

    fun checkServiceThread() {
      val currentThread = currentThread()
      if (currentThread is RBServiceThread) {
        return
      }

      this.logger.debug("bookmark service used wrong thread")
      throw IllegalStateException("Current thread is not the service thread")
    }
  }
}
