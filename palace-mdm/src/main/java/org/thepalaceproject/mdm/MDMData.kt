package org.thepalaceproject.mdm

/**
 * Data received from the managed device (MDM) configuration.
 */
data class MDMData(
  /**
   * @return The raw values as received from the device management system,
   * keyed by field name
   */
  val rawValues: Map<String, String>,
  /**
   * @return The result of parsing the raw values
   */
  val parseResult: MDMConfigurationParseResult,
)
