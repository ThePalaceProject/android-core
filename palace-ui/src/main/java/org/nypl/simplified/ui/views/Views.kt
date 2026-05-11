package org.nypl.simplified.ui.views

import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE

object Views {

  @JvmStatic
  fun setVisible(
    target: View,
    visible: Boolean
  ) {
    /*
     * Setting the visibility of a view in Android has a cost, even if that view is already in the desired
     * visibility state. Therefore, we don't try to set the visibility of a view if the view is already
     * in the right state.
     */
    when (target.visibility) {
      VISIBLE -> {
        if (!visible) {
          target.visibility = INVISIBLE
        }
      }

      INVISIBLE -> {
        if (visible) {
          target.visibility = VISIBLE
        }
      }

      GONE -> {
        if (visible) {
          target.visibility = VISIBLE
        }
      }
    }
  }
}
