package org.thepalaceproject.ui

import android.content.Context
import java.io.File

/**
 * An object that tracks whether the application is supposed to be using the new UI.
 */

object UIMigration {

  /**
   * @return `true` if the application is in "New UI" mode
   */

  fun isRunningNewUI(context: Context): Boolean {
    val markerFile = File(context.filesDir, "NewUI.txt")
    return markerFile.isFile
  }

  /**
   * Set the application to use (or not use) the "New UI"
   */

  fun setRunningNewUI(
    context: Context,
    running: Boolean
  ) {
    val markerFile = File(context.filesDir, "NewUI.txt")
    if (running) {
      markerFile.createNewFile()
    } else {
      markerFile.delete()
    }
  }
}
