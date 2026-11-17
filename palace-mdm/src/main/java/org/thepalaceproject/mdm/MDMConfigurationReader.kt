package org.thepalaceproject.mdm

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import org.slf4j.LoggerFactory

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
  private val logger =
    LoggerFactory.getLogger(MDMConfigurationReader::class.java)

  /**
   * @return The current managed device configuration
   */
  fun readConfiguration(): MDMConfigurationParseResult {
    val manager =
      this.context.getSystemService(RestrictionsManager::class.java)
    val bundle =
      manager.applicationRestrictions
    this.logger.debug("MDM application restrictions bundle: {}", bundle)
    return MDMConfigurationParser.parse(this.toValues(bundle))
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
