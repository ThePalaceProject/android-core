package org.nypl.simplified.accounts.registry.api

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import net.jcip.annotations.ThreadSafe
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * An object that exposes a map of debugging properties for influencing
 * account registry behaviour.
 */

@ThreadSafe
object AccountProviderRegistryDebugging {

  private val logger =
    LoggerFactory.getLogger(AccountProviderRegistryDebugging::class.java)

  private var preferences: SharedPreferences? = null
  private val propertiesNow: MutableMap<String, String> =
    ConcurrentHashMap()

  val properties: Map<String, String>
    get() = this.propertiesNow

  /**
   * Load any stored preferences from the disk.
   */

  fun load(context: Context) {
    try {
      val prefs = context.getSharedPreferences("AccountProviderRegistryDebugging", MODE_PRIVATE)
      this.preferences = prefs

      for (e in prefs.all) {
        val value = e.value
        if (value is String) {
          this.setProperty(e.key, value)
        }
      }
    } catch (e: Exception) {
      this.logger.debug("failed to load debugging preferences: ", e)
    }
  }

  /**
   * @return The value of the property `name`
   */

  fun property(name: String): String? =
    this.propertiesNow[name]

  /**
   * Set the value of the property `name` to `value`.
   */

  fun setProperty(
    name: String,
    value: String
  ) {
    try {
      this.propertiesNow.put(name, value)
      this.preferences
        ?.edit()
        ?.putString(name, value)
        ?.apply()
    } catch (e: Exception) {
      this.logger.debug("failed to set debugging property: ", e)
    }
  }

  /**
   * Remove the property `name`.
   */

  fun clearProperty(
    name: String
  ) {
    try {
      this.propertiesNow.remove(name)
      this.preferences
        ?.edit()
        ?.clear()
        ?.apply()
    } catch (e: Exception) {
      this.logger.debug("failed to set debugging property: ", e)
    }
  }
}
