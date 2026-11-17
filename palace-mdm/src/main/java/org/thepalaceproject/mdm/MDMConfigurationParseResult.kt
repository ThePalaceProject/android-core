package org.thepalaceproject.mdm

/**
 * The result of parsing a managed device configuration.
 */
data class MDMConfigurationParseResult(
  /**
   * @return The parsed configuration
   */
  val configuration: MDMConfiguration,
  /**
   * @return The fields whose values were present but invalid
   */
  val invalidFields: List<MDMConfigurationInvalidField>,
)
