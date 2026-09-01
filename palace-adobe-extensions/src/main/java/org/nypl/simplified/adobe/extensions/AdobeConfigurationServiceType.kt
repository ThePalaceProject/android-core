package org.nypl.simplified.adobe.extensions

import java.util.Optional

/**
 * Configuration values for the Adobe DRM.
 */

interface AdobeConfigurationServiceType {
  val packageOverride: String?

  fun packageOverrideOption(): Optional<String> = Optional.ofNullable(this.packageOverride)

  val debugLogging: Boolean

  val dataDirectoryName: String
}
