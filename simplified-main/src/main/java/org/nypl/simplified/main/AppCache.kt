package org.nypl.simplified.main

import android.content.Context
import android.content.SharedPreferences

/**
 * Util class for saving and retrieving app preferences that are handled locally
 */
class AppCache internal constructor(private val sharedPreferences: SharedPreferences) {
  constructor(context: Context) : this(
    context.getSharedPreferences("APP_PREFERENCES", Context.MODE_PRIVATE)
  )

  companion object {

    /**
     * SharedPreferences keys
     */
    private const val KEY_SEEN_TUTORIAL = "seen_tutorial"
  }

  /**
   * Confirms the tutorial was seen
   */
  fun confirmTutorialWasSeen() {
    sharedPreferences.edit().putBoolean(KEY_SEEN_TUTORIAL, true).apply()
  }

  /**
   * Checks if the tutorial was seen
   */
  fun wasTutorialSeen(): Boolean {
    return sharedPreferences.getBoolean(KEY_SEEN_TUTORIAL, false)
  }
}
