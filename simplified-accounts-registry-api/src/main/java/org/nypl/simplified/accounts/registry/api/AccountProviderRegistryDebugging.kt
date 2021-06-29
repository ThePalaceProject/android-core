package org.nypl.simplified.accounts.registry.api

import net.jcip.annotations.ThreadSafe
import java.util.concurrent.ConcurrentHashMap

/**
 * An object that exposes a map of debugging properties for influencing
 * account registry behaviour.
 */

@ThreadSafe
object AccountProviderRegistryDebugging {

  private val propertiesNow: MutableMap<String, String> =
    ConcurrentHashMap()

  val properties: Map<String, String>
    get() = this.propertiesNow

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
    this.propertiesNow.put(name, value)
  }

  /**
   * Remove the property `name`.
   */

  fun clearProperty(
    name: String
  ) {
    this.propertiesNow.remove(name)
  }
}
