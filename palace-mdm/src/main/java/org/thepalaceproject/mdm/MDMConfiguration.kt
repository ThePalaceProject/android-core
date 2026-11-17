package org.thepalaceproject.mdm

import java.net.URI

/**
 * A parsed managed device (MDM) configuration.
 *
 * The configuration is set by a device management system (e.g. Google Admin
 * Console) and names the libraries that the application must present to the
 * user.
 */
data class MDMConfiguration(
  /**
   * @return The registry UUIDs of the configured libraries, in the order in
   * which they appear in the configuration. Each value is a `urn:uuid:`
   * provider URI, in the form used by the library registry.
   */
  val libraryURIs: List<URI>,
)
