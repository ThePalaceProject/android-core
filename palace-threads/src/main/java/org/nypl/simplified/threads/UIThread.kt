package org.nypl.simplified.threads

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/**
 * Utility functions to execute code on the Android UI thread.
 */

object UIThread : Executor {
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
    if (isUIThread()) {
      return r.run()
    }

    val looper = Looper.getMainLooper()
    val h = Handler(looper)
    h.post(r)
  }

  override fun execute(r: Runnable) {
    this.runOnUIThread(r)
  }
}
