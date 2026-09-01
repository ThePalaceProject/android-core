package org.nypl.simplified.threads

import android.os.Handler
import android.os.Looper
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

/**
 * Utility functions to execute code on the Android UI thread.
 */

object UIThread : Executor {

  private val logger =
    LoggerFactory.getLogger(UIThread::class.java)

  /**
   * Check that the current thread is the UI thread and raise {@link IllegalStateException}
   * if it isn't.
   */

  fun checkIsUIThread() {
    if (isUIThread() == false) {
      throw IllegalStateException(
        String.format(
          "Current thread '%s' is not the Android UI thread",
          Thread.currentThread(),
        ),
      )
    }
  }

  /**
   * @return `true` iff the current thread is the UI thread.
   */

  fun isUIThread(): Boolean = Looper.getMainLooper().thread === Thread.currentThread()

  /**
   * Run the given Runnable on the UI thread.
   *
   * @param r The runnable
   */

  fun runOnUIThread(r: Runnable) {
    val caller = RuntimeException().stackTrace[1]

    val safeRunnable = Runnable {
      try {
        r.run()
      } catch (e: Throwable) {
        logger.debug(
          "UI thread runnable threw exception: {}:{}:{}",
          caller.className,
          caller.methodName,
          caller.lineNumber,
          e)
      }
    }

    if (isUIThread()) {
      return safeRunnable.run()
    }

    val looper = Looper.getMainLooper()
    val h = Handler(looper)
    h.post(safeRunnable)
  }

  override fun execute(r: Runnable) {
    this.runOnUIThread(r)
  }
}
