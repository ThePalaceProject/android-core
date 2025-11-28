package org.nypl.simplified.ui.catalog

import android.content.res.Configuration
import android.view.ViewGroup

sealed class CatalogFeedView {

  abstract val root: ViewGroup

  /**
   * Set the current screen's focus to an appropriate starting focus (such as selecting the first
   * button in the toolbar).
   */

  abstract fun startFocus()

  /**
   * Clear all view state and subscriptions.
   */

  abstract fun clear()

  /**
   * Set the current screen's focus to an appropriate starting focus (such as selecting the first
   * button in the toolbar).
   */

  fun startFocusDelayed() {
    this.root.postDelayed({
      this.startFocus()
    }, 100L)
  }

  /**
   * The configuration has changed (such as screen orientation).
   */

  abstract fun onConfigurationChanged(newConfig: Configuration)
}
