package org.thepalaceproject.mdm

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * A reader for the managed device configuration from the system
 * [RestrictionsManager].
 *
 * On devices that are not managed, [readConfiguration] returns a
 * configuration with no libraries and no invalid fields.
 */
class MDMConfigurationReader(
  private val context: Context,
) {
  /**
   * @return The current managed device configuration
   */
  fun readConfiguration(): MDMConfigurationParseResult {
    val manager =
      this.context.getSystemService(RestrictionsManager::class.java)
    return MDMConfigurationParser.parse(this.toValues(manager.applicationRestrictions))
  }

  private fun toValues(bundle: Bundle?): Map<String, String> {
    if (bundle == null) {
      return emptyMap()
    }
    val values =
      mutableMapOf<String, String>()
    for (key in bundle.keySet()) {
      val value =
        bundle.getString(key)
      if (value != null) {
        values[key] = value
      }
    }
    return values
  }
}
