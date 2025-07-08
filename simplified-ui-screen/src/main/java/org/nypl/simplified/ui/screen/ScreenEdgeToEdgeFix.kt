package org.nypl.simplified.ui.screen

import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15 has decided to mandate "edge-to-edge" mode for applications, for reasons unknown.
 * This means that the application views will overlap what used to be the status bar at the top
 * of the screen, and whatever used to be on the bottom of the screen.
 *
 * The methods in this class set insets as needed so that this no longer happens.
 */

object ScreenEdgeToEdgeFix {

  /**
   * Set insets for the given view to work around the new edge-to-edge display. Call this method
   * on the root view of a hierarchy as soon as the views are created. This typically only needs
   * to be done once per activity.
   */

  fun edgeToEdge(rootView: View) {
    if (Build.VERSION.SDK_INT >= 35) {
      ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val padLeft = systemBars.left
        val padRight = systemBars.right
        val padTop = systemBars.top
        val padBottom = systemBars.bottom
        view.setPadding(padLeft, padTop, padRight, padBottom)
        insets
      }
    }
  }
}
