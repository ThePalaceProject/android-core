package org.thepalaceproject.mdm

/**
 * A managed configuration field whose value was present but invalid.
 */
data class MDMConfigurationInvalidField(
  /**
   * @return The name of the field (e.g. `Library-3`)
   */
  val field: String,
  /**
   * @return The value that was present in the configuration
   */
  val value: String,
)
